package app.accentury.backend.analysis;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * 억양 학습 채점(KAN-267, §3.19)의 결과를 받는 곳 - {@code learning} 패키지가 구현한다.
 * <p>
 * 상태 전이({@link AnalysisLedger})에 더해 음성 저장을 맡는다. 레벨테스트의 학습 샘플({@code TrainingSample})과
 * 키 모양과 메타가 달라서다 (음성 버킷의 {@code _learning} 트리, 2026-10-10 결정). 호출 시점은 레벨테스트와 같다 -
 * 상태 전이가 끝난 뒤, 오디오 버퍼를 지우기 전이다. 구현은 예외를 밖으로 내지 않는다.
 */
public interface LearningAnalysisSink extends AnalysisLedger {

    /**
     * 음성 저장에 동의한 계정이면 음성과 메타를 남긴다. 동의가 없거나 결과가 계약 위반, AI 불가({@code null})면
     * 아무것도 하지 않는다.
     */
    void keepVoice(AnalysisDispatcher.AnalysisRequest request, AiAnalysisClient.@Nullable Outcome outcome,
                   String correlationId);

    /**
     * 학습 채점이 조립되지 않은 자리 - 학습 요청이 오면 시작을 거절해 AI를 부르지 않는다. 학습 업로드 경로가 있는
     * 배포에서는 언제나 실제 구현이 있으므로, 여기 닿으면 조립 버그다.
     */
    LearningAnalysisSink NONE = new LearningAnalysisSink() {
        @Override
        public boolean start(String jobId) {
            LoggerFactory.getLogger(LearningAnalysisSink.class)
                    .error("학습 채점 기록이 조립되지 않았는데 학습 요청이 왔다 jobId={}", jobId);
            return false;
        }

        @Override
        public boolean complete(String jobId, AiAnalysisClient.Completed completed) {
            return false;
        }

        @Override
        public void fail(String jobId, AnalysisJobStatus failedStatus, String errorCode) {
        }

        @Override
        public int failAll(Collection<String> jobIds, AnalysisJobStatus failedStatus, String errorCode) {
            return 0;
        }

        @Override
        public void keepVoice(AnalysisDispatcher.AnalysisRequest request, AiAnalysisClient.@Nullable Outcome outcome,
                              String correlationId) {
        }
    };
}
