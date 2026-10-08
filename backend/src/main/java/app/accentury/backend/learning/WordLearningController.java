package app.accentury.backend.learning;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단어 학습 API (KAN-265, 명세서 §3.16) - 전부 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * <p>
 * 응답은 계정 상태에 따라 달라질 수 있으므로(KAN-268의 완료 표시와 추천) 캐시하지 않는다.
 */
@RestController
@RequestMapping("/v0/learning")
class WordLearningController {

    private final WordLearningService service;

    WordLearningController(WordLearningService service) {
        this.service = service;
    }

    /** 어휘 세트 목록 (W-1). 200 / 401. */
    @GetMapping("/word-sets")
    ResponseEntity<WordSetListResponse> listSets(@AuthenticatedUser AppUser user) {
        return noStore(service.listSets());
    }

    /** 세트 상세 - 카드와 문항, 정답 없음 (W-2, W-3). 200 / 401 / 404 {@code LEARNING_SET_NOT_FOUND}. */
    @GetMapping("/word-sets/{setId}")
    ResponseEntity<WordSetResponse> setDetail(@AuthenticatedUser AppUser user, @PathVariable String setId) {
        return noStore(service.setDetail(setId));
    }

    /** 시도 시작 - 본문 없음. 201 / 401 / 404 {@code LEARNING_SET_NOT_FOUND} / 429. */
    @PostMapping("/word-sets/{setId}/attempts")
    ResponseEntity<WordAttemptResponse> startAttempt(@AuthenticatedUser AppUser user, @PathVariable String setId) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.startAttempt(user, setId));
    }

    /**
     * 답안 제출과 채점 (W-4). {@code Idempotency-Key} 필수 - 누락 검증은 서비스가 맡아 공통 오류 봉투로 답하므로
     * {@code required = false}다.
     * <p>
     * 200 / 400 {@code VALIDATION_FAILED} / 401 / 403 {@code LEARNING_ATTEMPT_FORBIDDEN} / 404 {@code LEARNING_ATTEMPT_NOT_FOUND} /
     * 409 {@code LEARNING_ATTEMPT_COMPLETED}, {@code ITEM_ALREADY_ANSWERED} / 422 {@code ITEM_NOT_IN_VERSION} / 429.
     */
    @PostMapping(value = "/word-attempts/{attemptId}/items/{itemId}/answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<WordAnswerResponse> answer(@AuthenticatedUser AppUser user,
                                              @PathVariable String attemptId,
                                              @PathVariable String itemId,
                                              @RequestHeader(value = "Idempotency-Key", required = false)
                                              @Nullable String idempotencyKey,
                                              @RequestBody(required = false) @Nullable WordAnswerRequest request) {
        return noStore(service.answer(user, attemptId, itemId, idempotencyKey, request));
    }

    /**
     * 세트 완료 - 정답률과 오답 목록 (W-5). 본문 없음, 멱등.
     * <p>
     * 200 / 401 / 403 / 404 / 422 {@code LEARNING_ATTEMPT_INCOMPLETE} (+ {@code missingItems}) / 429.
     */
    @PostMapping("/word-attempts/{attemptId}/complete")
    ResponseEntity<WordAttemptResultResponse> complete(@AuthenticatedUser AppUser user, @PathVariable String attemptId) {
        return noStore(service.complete(user, attemptId));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
