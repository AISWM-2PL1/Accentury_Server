package app.accentury.backend.learning;

import app.accentury.backend.analysis.AnalysisJobStatus;

/**
 * 억양 녹음 업로드 202 응답 (KAN-267, 명세서 §3.19). 같은 키의 재전송이면 {@code status}는 그 시점의 값이다.
 */
record IntonationUploadResponse(String attemptId, String contentVersion, String cardId,
                                AnalysisJobStatus status, long pollAfterMs) {

    static IntonationUploadResponse from(IntonationAttempt attempt, long pollAfterMs) {
        return new IntonationUploadResponse(attempt.id(), attempt.contentVersion(), attempt.cardId(),
                attempt.status(), pollAfterMs);
    }
}
