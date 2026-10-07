package app.accentury.backend.learning;

import java.time.Instant;

/**
 * {@code POST /v0/learning/word-sets/{setId}/attempts}의 201 응답 (명세서 §3.16).
 *
 * @param attemptId      시도 식별자 - 답안과 완료 경로가 쓴다
 * @param contentVersion 시도가 고정한 발행본
 * @param itemCount      이 세트의 문항 수 - 완료에 필요한 답안 수
 */
public record WordAttemptResponse(String attemptId, String contentVersion, String setId, int itemCount,
                                  Instant startedAt) {

    static WordAttemptResponse from(WordSetAttempt attempt) {
        return new WordAttemptResponse(attempt.id(), attempt.contentVersion(), attempt.setId(),
                attempt.itemCount(), attempt.startedAt());
    }
}
