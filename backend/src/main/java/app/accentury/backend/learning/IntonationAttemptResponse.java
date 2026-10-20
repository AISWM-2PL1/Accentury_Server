package app.accentury.backend.learning;

import app.accentury.backend.analysis.AnalysisJobStatus;
import app.accentury.backend.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 억양 학습 채점 결과 조회 응답 (KAN-267, 명세서 §3.19).
 * <p>
 * {@code score}, {@code pitchFeedback}, {@code previousScore}, {@code modelVersion}, {@code scoreVersion}은 완료일 때만,
 * {@code error}는 실패일 때만 실린다. {@code previousScore}는 완료인데 직전 시도가 없으면 키는 있고 값이 null이어야
 * 한다 - 그래서 {@link Optional}로 둔다. 이 자리의 null(완료가 아님)은 키째 빠지고, 빈 Optional(직전 없음)은 null로
 * 나간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record IntonationAttemptResponse(
        String attemptId,
        String contentVersion,
        String cardId,
        AnalysisJobStatus status,
        long pollAfterMs,
        @Nullable Integer score,
        @Nullable List<PitchFeedback> pitchFeedback,
        @Nullable Optional<Integer> previousScore,
        @Nullable Error error,
        @Nullable String modelVersion,
        @Nullable String scoreVersion) {

    /** 실패 사유 - §3.4의 error와 같은 모양이다. {@code retryable}은 상태가 RETRYABLE_FAILED인지에서 나온다. */
    record Error(String code, boolean retryable) {
    }

    static IntonationAttemptResponse processing(IntonationAttempt attempt, long pollAfterMs) {
        return new IntonationAttemptResponse(attempt.id(), attempt.contentVersion(), attempt.cardId(),
                attempt.status(), pollAfterMs, null, null, null, null, null, null);
    }

    static IntonationAttemptResponse completed(IntonationAttempt attempt, long pollAfterMs, int score,
                                               List<PitchFeedback> feedback, @Nullable Integer previousScore) {
        return new IntonationAttemptResponse(attempt.id(), attempt.contentVersion(), attempt.cardId(),
                attempt.status(), pollAfterMs, score, feedback, Optional.ofNullable(previousScore), null,
                attempt.modelVersion(), attempt.scoreVersion());
    }

    static IntonationAttemptResponse failed(IntonationAttempt attempt, long pollAfterMs) {
        String code = attempt.errorCode() != null ? attempt.errorCode() : ErrorCode.INTERNAL_ERROR.name();
        return new IntonationAttemptResponse(attempt.id(), attempt.contentVersion(), attempt.cardId(),
                attempt.status(), pollAfterMs, null, null, null,
                new Error(code, attempt.status() == AnalysisJobStatus.RETRYABLE_FAILED), null, null);
    }
}
