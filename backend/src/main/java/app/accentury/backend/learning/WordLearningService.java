package app.accentury.backend.learning;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AppUserRepository;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.IdempotencyKeys;
import app.accentury.backend.common.ItemsApiException;
import app.accentury.backend.common.RateLimits;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 단어 학습의 조회, 시도, 채점, 완료 (KAN-265, API 명세서 §3.16).
 * <p>
 * 답안 저장과 완료 전이는 시도 행 잠금 아래 한 트랜잭션이다 ({@code VocabAnswerService}와 같은 규칙) - 잠금이 없으면
 * 완료 가드와 저장 사이에 완료 전이가 끼어들어 확정된 시도에 답안이 추가된다. 같은 문항의 동시 제출도 이 잠금으로
 * 직렬화되므로 (attempt_id, item_id) 유니크 제약은 마지막 안전망이다.
 * <p>
 * <b>쓰기 트랜잭션은 계정 행 잠금으로 시작한다</b> (Codex 리뷰 P2). 인증 단계에서 읽은 계정은 트랜잭션 밖의 스냅샷이라,
 * 그 사이 탈퇴가 커밋되면 파기({@link LearningRecords#purge}) 뒤에 새 시도가 들어가 탈퇴한 계정에 학습 기록이 영구히
 * 남는다 (탈퇴는 계정 행을 지우지 않아 FK가 막지 못한다). 탈퇴({@code WithdrawalService})와 같은 순서(계정 -> 시도)로
 * 잠그므로 교착도 없다 - 시도 행을 먼저 잠그고 오답 INSERT의 FK 검사가 계정 행을 기다리면, 계정을 잠근 채 시도 행을
 * 지우려는 탈퇴와 서로 기다린다. 탈퇴가 먼저 커밋됐으면 401 {@code AUTH_TOKEN_INVALID}다 (§2.1의 계정 확인과 같다).
 * <p>
 * 시각은 마이크로초로 자른다 - 열이 {@code timestamp(6)}라 나노초가 있는 플랫폼(Linux)에서는 메모리의 값과 DB에서 다시
 * 읽은 값이 달라져 "완료 재호출은 같은 본문"(§3.16)이 깨진다 (Codex 리뷰 P2).
 * <p>
 * 로그에는 시도 id, 세트 id, 문항 id만 남긴다 - 고른 보기와 정오는 남기지 않는다 (§2.6의 취지).
 */
@Service
public class WordLearningService {

    private static final Logger log = LoggerFactory.getLogger(WordLearningService.class);

    private final WordLearningRegistry registry;
    private final AppUserRepository users;
    private final WordSetAttemptRepository attempts;
    private final WordAttemptAnswerRepository answers;
    private final WordWrongAnswerRepository wrongAnswers;
    private final TransactionTemplate transactionTemplate;
    private final RateLimits rateLimits;

    public WordLearningService(WordLearningRegistry registry, AppUserRepository users,
                               WordSetAttemptRepository attempts, WordAttemptAnswerRepository answers,
                               WordWrongAnswerRepository wrongAnswers, TransactionTemplate transactionTemplate,
                               RateLimits rateLimits) {
        this.registry = registry;
        this.users = users;
        this.attempts = attempts;
        this.answers = answers;
        this.wrongAnswers = wrongAnswers;
        this.transactionTemplate = transactionTemplate;
        this.rateLimits = rateLimits;
    }

    /** 세트 목록 (W-1) - 현재 발행본. 완료 표시와 추천은 KAN-268이다. */
    WordSetListResponse listSets() {
        return registry.current().listResponse();
    }

    /** 세트 상세 (W-2, W-3) - 정답과 해설 없음. 없는 세트는 404. */
    WordSetResponse setDetail(String setId) {
        return registry.current().setResponse(setId);
    }

    /** 시도 시작 - 현재 발행본의 세트에 고정된 시도 한 행. 멱등 키는 없다 (§3.16). */
    WordAttemptResponse startAttempt(AppUser user, String setId) {
        rateLimits.check(RateLimits.Scope.WORD_LEARNING, user.id().toString());
        WordLearningRegistry.Published current = registry.current();
        WordLearningDefinition.Set set = current.set(setId);
        WordSetAttempt attempt = Objects.requireNonNull(transactionTemplate.execute(tx -> {
            requireActiveAccount(user.id());
            return attempts.save(new WordSetAttempt("wa_" + UUID.randomUUID(), user.id(),
                    current.definition().contentVersion(), set.setId(), set.items().size(), now()));
        }));
        log.info("단어 학습 시도 시작 attemptId={} setId={} contentVersion={}", attempt.id(), set.setId(),
                attempt.contentVersion());
        return WordAttemptResponse.from(attempt);
    }

    /**
     * 답안 제출과 채점 (W-4).
     * <p>
     * 검증 순서: 요청 제한 -> 멱등 키와 본문 -> (잠금) 계정 생존 -> (잠금) 시도 존재와 소유 -> 문항과 보기 -> 완료 가드 ->
     * 멱등 판별 -> 저장.
     * 소유 검사가 문항 검증보다 앞인 것은 남의 시도에 어떤 정보도 주지 않기 위해서다. 완료 가드가 멱등 판별보다
     * 먼저인 것은 §3.5와 같다 - 완료 뒤에는 같은 키의 재전송도 409다.
     */
    WordAnswerResponse answer(AppUser user, String attemptId, String itemId, @Nullable String idempotencyKey,
                              @Nullable WordAnswerRequest request) {
        rateLimits.check(RateLimits.Scope.WORD_LEARNING, user.id().toString());
        String key = IdempotencyKeys.require(idempotencyKey);
        String choiceId = requireChoiceId(request);

        Answered answered = Objects.requireNonNull(transactionTemplate.execute(tx -> {
            requireActiveAccount(user.id());
            WordSetAttempt attempt = lockOwned(attemptId, user.id());
            WordLearningDefinition.Set set = registry.get(attempt.contentVersion()).set(attempt.setId());
            WordLearningDefinition.Item item = set.item(itemId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_IN_VERSION, "이 세트의 문항이 아닙니다."));
            // 이 문항의 보기가 아니면 거절한다 - 같은 세트의 다른 문항 보기도 포함 (§3.16).
            if (item.choice(choiceId).isEmpty()) {
                throw new ApiException(ErrorCode.ITEM_NOT_IN_VERSION, "이 문항의 선택지가 아닙니다.");
            }
            if (attempt.isCompleted()) {
                throw new ApiException(ErrorCode.LEARNING_ATTEMPT_COMPLETED);
            }
            var existing = answers.findByAttemptIdAndItemId(attempt.id(), itemId);
            if (existing.isPresent()) {
                requireSameReplay(existing.get(), key, choiceId);
                return new Answered(false, response(attempt, item, existing.get().correct()));
            }
            boolean correct = choiceId.equals(item.correctChoiceId());
            Instant now = now();
            answers.save(new WordAttemptAnswer("waa_" + UUID.randomUUID(), attempt.id(), itemId, choiceId, correct,
                    key, now));
            if (!correct) {
                // 오답은 계정 기준으로 쌓인다 (FR-WD-04). 같은 키의 재전송은 위에서 돌아가므로 여기 오지 않는다.
                wrongAnswers.recordWrong("wwa_" + UUID.randomUUID(), user.id(), attempt.contentVersion(),
                        attempt.setId(), itemId, now);
            }
            return new Answered(true, response(attempt, item, correct));
        }));

        if (answered.savedNew()) {
            log.info("단어 학습 답안 저장 attemptId={} itemId={}", attemptId, itemId);
        }
        return answered.response();
    }

    private record Answered(boolean savedNew, WordAnswerResponse response) {
    }

    /**
     * 세트 완료 (W-5) - 전 문항의 답안이 있어야 하고, 저장된 답안 행에서 정답률과 오답 목록을 낸다.
     * 완료된 시도에 다시 부르면 같은 본문이다 (멱등).
     */
    WordAttemptResultResponse complete(AppUser user, String attemptId) {
        rateLimits.check(RateLimits.Scope.WORD_LEARNING, user.id().toString());
        Completed completed = Objects.requireNonNull(transactionTemplate.execute(tx -> {
            requireActiveAccount(user.id());
            WordSetAttempt attempt = lockOwned(attemptId, user.id());
            WordLearningDefinition.Set set = registry.get(attempt.contentVersion()).set(attempt.setId());
            Map<String, WordAttemptAnswer> byItem = answers.findByAttemptId(attempt.id()).stream()
                    .collect(Collectors.toMap(WordAttemptAnswer::itemId, Function.identity()));
            boolean transitioned = false;
            if (!attempt.isCompleted()) {
                List<String> missing = set.items().stream()
                        .map(WordLearningDefinition.Item::itemId)
                        .filter(id -> !byItem.containsKey(id))
                        .toList();
                if (!missing.isEmpty()) {
                    throw new ItemsApiException(ErrorCode.LEARNING_ATTEMPT_INCOMPLETE,
                            ItemsApiException.ItemsField.MISSING_ITEMS, missing);
                }
                int correctCount = (int) byItem.values().stream().filter(WordAttemptAnswer::correct).count();
                attempt.complete(now(), correctCount);
                transitioned = true;
            }
            return new Completed(transitioned, result(attempt, set, byItem));
        }));
        if (completed.transitioned()) {
            log.info("단어 학습 세트 완료 attemptId={} setId={}", attemptId, completed.response().setId());
        }
        return completed.response();
    }

    private record Completed(boolean transitioned, WordAttemptResultResponse response) {
    }

    /**
     * 계정 행을 잠그고 살아 있는지 다시 본다 - 쓰기 트랜잭션의 첫 잠금이다 (클래스 javadoc). 탈퇴가 먼저 커밋됐으면
     * 401이고, 진행 중인 탈퇴가 있으면 그 커밋을 기다린 뒤 401이다.
     */
    private void requireActiveAccount(UUID userId) {
        users.lockActive(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
    }

    /** DB 열 정밀도(마이크로초)로 자른 현재 시각 - 응답과 재조회 값이 같아야 한다 (클래스 javadoc). */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 시도 행을 잠그고 소유를 확인한다. 없으면 404, 남의 것이면 403 (FR-AC-11). 잠금은 완료 가드와 저장을 한
     * 트랜잭션으로 묶는 것이라 트랜잭션 안에서만 부른다.
     */
    private WordSetAttempt lockOwned(String attemptId, UUID userId) {
        WordSetAttempt attempt = attempts.lockById(attemptId)
                .orElseThrow(() -> new ApiException(ErrorCode.LEARNING_ATTEMPT_NOT_FOUND));
        if (!attempt.belongsTo(userId)) {
            throw new ApiException(ErrorCode.LEARNING_ATTEMPT_FORBIDDEN);
        }
        return attempt;
    }

    /**
     * 이미 답안이 있는 문항의 처리 - 같은 키의 동일 요청만 재전송으로 인정한다 (§5.2, {@code VocabAnswerService}와 동일).
     * 같은 키 + 같은 보기는 통과(처음 결과 반환), 같은 키 + 다른 보기는 400, 다른 키는 409.
     */
    private static void requireSameReplay(WordAttemptAnswer stored, String key, String choiceId) {
        if (!stored.idempotencyKey().equals(key)) {
            throw new ApiException(ErrorCode.ITEM_ALREADY_ANSWERED);
        }
        if (!stored.choiceId().equals(choiceId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "같은 Idempotency-Key로 다른 답이 제출되었습니다.");
        }
    }

    /** 제출 응답 - 진행도는 잠금 아래에서 읽은 현재 값이다 (재전송도 같다). */
    private WordAnswerResponse response(WordSetAttempt attempt, WordLearningDefinition.Item item, boolean correct) {
        int answeredCount = (int) answers.countByAttemptId(attempt.id());
        return new WordAnswerResponse(correct, item.correctChoiceId(), item.correctText(), item.explanation(),
                answeredCount, attempt.itemCount());
    }

    private static WordAttemptResultResponse result(WordSetAttempt attempt, WordLearningDefinition.Set set,
                                                    Map<String, WordAttemptAnswer> byItem) {
        List<WordAttemptResultResponse.WrongItem> wrong = new ArrayList<>();
        for (WordLearningDefinition.Item item : set.items()) {
            WordAttemptAnswer answer = byItem.get(item.itemId());
            if (answer == null || answer.correct()) {
                continue;
            }
            wrong.add(new WordAttemptResultResponse.WrongItem(item.itemId(), item.cardId(), item.prompt(),
                    answer.choiceId(), item.choice(answer.choiceId()).orElseThrow().text(),
                    item.correctChoiceId(), item.correctText(), item.explanation()));
        }
        int correctCount = Objects.requireNonNull(attempt.correctCount(), "완료된 시도의 정답 수가 없다");
        return new WordAttemptResultResponse(attempt.id(), attempt.contentVersion(), attempt.setId(),
                attempt.itemCount(), correctCount,
                WordAttemptResultResponse.accuracyPercent(correctCount, attempt.itemCount()), List.copyOf(wrong),
                Objects.requireNonNull(attempt.completedAt(), "완료된 시도의 완료 시각이 없다"));
    }

    private static String requireChoiceId(@Nullable WordAnswerRequest request) {
        if (request == null || request.choiceId() == null || request.choiceId().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "choiceId가 필요합니다.");
        }
        return request.choiceId();
    }
}
