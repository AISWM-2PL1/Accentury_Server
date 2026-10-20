package app.accentury.backend.analysis;

import java.util.Collection;

/**
 * 분석 1건의 상태를 적는 곳 - 전달 워커({@link HttpAnalysisDispatcher})가 AI 결과를 여기에 종결한다 (KAN-267).
 * <p>
 * 레벨테스트는 {@code analysis_job}({@link AnalysisJobTransitions}), 억양 학습 채점은
 * {@code intonation_learning_attempt}({@link LearningAnalysisSink})다. 두 기록이 같은 전달 큐와 같은 재전송,
 * 회로 규칙을 지나도록 워커는 이 경계만 안다. 모든 전이는 "PROCESSING일 때만"의 조건부 갱신이다 - 늦게 온 결과가
 * 이미 종결된 기록을 덮지 않는다.
 */
public interface AnalysisLedger {

    /** 실행 시작을 찍는다 - 이미 종결됐거나 없는 기록이면 false이고, 워커는 AI를 부르지 않는다. */
    boolean start(String jobId);

    /** 분석 성공으로 종결한다 - 이미 종결됐거나 없는 기록이면 false다. */
    boolean complete(String jobId, AiAnalysisClient.Completed completed);

    /** 실패로 종결한다 - {@code failedStatus}는 RETRYABLE_FAILED나 FAILED다. */
    void fail(String jobId, AnalysisJobStatus failedStatus, String errorCode);

    /** 여러 건을 한 번에 실패로 종결한다 (종료 배수, KAN-166) - 실제로 종결된 건수를 돌려준다. */
    int failAll(Collection<String> jobIds, AnalysisJobStatus failedStatus, String errorCode);
}
