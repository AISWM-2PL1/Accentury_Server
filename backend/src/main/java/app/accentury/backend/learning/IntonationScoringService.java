package app.accentury.backend.learning;

import app.accentury.backend.analysis.AnalysisDispatcher;
import app.accentury.backend.analysis.AnalysisJobStatus;
import app.accentury.backend.analysis.PollIntervals;
import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AppUserRepository;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.IdempotencyKeys;
import app.accentury.backend.common.RateLimits;
import app.accentury.backend.training.VoiceConsents;
import app.accentury.backend.upload.VoiceRecordings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 억양 학습 채점 (KAN-267, 명세서 §3.19) - 카드 1건 녹음 업로드와 결과 조회.
 * <p>
 * 업로드 흐름은 레벨테스트(§3.3, {@code VoiceUploadService})를 따른다. 요청 제한, 멱등 키, 녹음 검증
 * ({@link VoiceRecordings}) 뒤 한 트랜잭션에서 계정 행을 잠그고(탈퇴와 경합하지 않게 - 단어 학습과 같은 첫 잠금),
 * 같은 키의 시도가 있으면 그대로 돌려주고, 없으면 회로 허용을 확인한 뒤 시도를 만든다. 전달은 커밋 뒤 트랜잭션 밖에서
 * 레벨테스트와 같은 큐로 한다 (2026-10-10 결정). 카드당 시도 상한은 없다 - 일일 제한은 팀 결정이다 (§7).
 * <p>
 * 오디오 사본의 파기 규칙도 같다 (KAN-27) - 전달 전에 끊기는 모든 경로에서 여기서 0으로 덮고, 전달 호출과 함께 소유권이
 * 디스패처로 넘어간다.
 */
@Service
public class IntonationScoringService {

    private static final Logger log = LoggerFactory.getLogger(IntonationScoringService.class);

    private static final TypeReference<List<PitchFeedback>> FEEDBACK_LIST = new TypeReference<>() {
    };

    private final IntonationLearningRegistry registry;
    private final IntonationAttemptRepository attempts;
    private final IntonationAttemptLedger ledger;
    private final IntonationScoring scoring;
    private final AppUserRepository users;
    private final AnalysisDispatcher dispatcher;
    private final PollIntervals pollIntervals;
    private final VoiceConsents voiceConsents;
    private final RateLimits rateLimits;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    IntonationScoringService(IntonationLearningRegistry registry, IntonationAttemptRepository attempts,
                             IntonationAttemptLedger ledger, IntonationScoring scoring, AppUserRepository users,
                             AnalysisDispatcher dispatcher, PollIntervals pollIntervals, VoiceConsents voiceConsents,
                             RateLimits rateLimits, TransactionTemplate transactionTemplate,
                             ObjectMapper objectMapper) {
        this.registry = registry;
        this.attempts = attempts;
        this.ledger = ledger;
        this.scoring = scoring;
        this.users = users;
        this.dispatcher = dispatcher;
        this.pollIntervals = pollIntervals;
        this.voiceConsents = voiceConsents;
        this.rateLimits = rateLimits;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 카드 1건 녹음 업로드 - 202. 같은 키의 재전송은 분석을 다시 돌리지 않고 처음 시도를 돌려준다 (§5.2). */
    IntonationUploadResponse upload(AppUser user, String cardId, @Nullable String idempotencyKey,
                                    @Nullable MultipartFile audio, @Nullable String metaJson) {
        rateLimits.check(RateLimits.Scope.INTONATION_LEARNING, user.id().toString());
        String key = IdempotencyKeys.require(idempotencyKey);
        IntonationLearningRegistry.Published published = registry.current();
        String contentVersion = published.definition().contentVersion();
        IntonationLearningDefinition.Card card = published.card(cardId);
        VoiceRecordings.Recording recording = VoiceRecordings.read(objectMapper, audio, metaJson);
        byte[] audioBytes = recording.audio();

        boolean transferred = false;
        try {
            long pollAfterMs = pollIntervals.pollAfterMs();
            String newAttemptId = "la_" + UUID.randomUUID();
            // accepts()가 반열림 복구 시험 자리를 이 시도 앞으로 잡을 수 있다 (KAN-28) - 롤백되면 놓아준다.
            AtomicBoolean claimedTrial = new AtomicBoolean();
            IntonationAttempt attempt;
            try {
                attempt = Objects.requireNonNull(transactionTemplate.execute(tx -> {
                    users.lockActive(user.id()).orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
                    var existing = attempts.findByUserIdAndContentVersionAndCardIdAndIdempotencyKey(
                            user.id(), contentVersion, cardId, key);
                    if (existing.isPresent()) {
                        return existing.get();
                    }
                    // 회로가 받지 않으면 시도를 만들지 않는다 - 오디오를 저장하지 않아 나중에 보낼 수 없다 (§3.3과 같다).
                    if (!dispatcher.accepts(newAttemptId)) {
                        throw new ApiException(ErrorCode.ANALYSIS_UNAVAILABLE);
                    }
                    claimedTrial.set(true);
                    return attempts.save(new IntonationAttempt(newAttemptId, user.id(), contentVersion, cardId, key,
                            Instant.now().truncatedTo(ChronoUnit.MICROS)));
                }));
            } catch (RuntimeException e) {
                if (claimedTrial.get()) {
                    dispatcher.abandon(newAttemptId);
                }
                throw e;
            }
            if (!attempt.id().equals(newAttemptId)) {
                return IntonationUploadResponse.from(attempt, pollAfterMs);
            }

            // 동의는 업로드마다 지금 계정의 값을 본다 (KAN-269, §3.15). 없으면 음성은 어디에도 남지 않는다.
            AnalysisDispatcher.AnalysisRequest request = AnalysisDispatcher.AnalysisRequest.forLearning(
                    attempt.id(), cardId, card.scriptKey(), contentVersion, scoring.scoreVersion(),
                    user.region() != null ? user.region().name() : null, recording.durationMs(),
                    voiceConsents.forAccount(user), audioBytes);
            transferred = true;
            try {
                dispatcher.dispatch(request);
            } catch (RuntimeException e) {
                // 전달 실패를 PROCESSING으로 두면 오디오가 없어 영영 끝나지 않는다 - 재녹음(새 키)을 유도하고 503이다.
                ledger.fail(attempt.id(), AnalysisJobStatus.RETRYABLE_FAILED, ErrorCode.ANALYSIS_UNAVAILABLE.name());
                dispatcher.abandon(attempt.id());
                log.warn("학습 채점 전달 실패 attemptId={} cardId={}", attempt.id(), cardId, e);
                throw new ApiException(ErrorCode.ANALYSIS_UNAVAILABLE);
            }
            log.info("학습 녹음 접수 attemptId={} cardId={}", attempt.id(), cardId);
            return IntonationUploadResponse.from(attempt, pollAfterMs);
        } finally {
            if (!transferred) {
                AnalysisDispatcher.AnalysisRequest.wipe(audioBytes);
            }
        }
    }

    /** 결과 조회 - 없으면 404, 남의 시도면 403 (FR-AC-11). */
    IntonationAttemptResponse status(AppUser user, String attemptId) {
        IntonationAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(ErrorCode.LEARNING_ATTEMPT_NOT_FOUND));
        if (!attempt.belongsTo(user.id())) {
            throw new ApiException(ErrorCode.LEARNING_ATTEMPT_FORBIDDEN);
        }
        long pollAfterMs = pollIntervals.pollAfterMs();
        return switch (attempt.status()) {
            case PROCESSING -> IntonationAttemptResponse.processing(attempt, pollAfterMs);
            case COMPLETED -> {
                Integer previous = attempts
                        .findFirstByUserIdAndContentVersionAndCardIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                                user.id(), attempt.contentVersion(), attempt.cardId(), AnalysisJobStatus.COMPLETED,
                                attempt.createdAt())
                        .map(IntonationAttempt::score)
                        .orElse(null);
                yield IntonationAttemptResponse.completed(attempt, pollAfterMs,
                        Objects.requireNonNull(attempt.score(), "완료된 시도에는 점수가 있다"),
                        readFeedback(attempt), previous);
            }
            case RETRYABLE_FAILED, FAILED -> IntonationAttemptResponse.failed(attempt, pollAfterMs);
        };
    }

    private List<PitchFeedback> readFeedback(IntonationAttempt attempt) {
        String json = attempt.pitchFeedback();
        return json == null ? List.of() : objectMapper.readValue(json, FEEDBACK_LIST);
    }
}
