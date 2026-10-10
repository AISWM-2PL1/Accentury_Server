package app.accentury.backend.learning;

import app.accentury.backend.analysis.AiAnalysisClient;
import app.accentury.backend.analysis.AnalysisDispatcher;
import app.accentury.backend.analysis.AnalysisJobStatus;
import app.accentury.backend.analysis.LearningAnalysisSink;
import app.accentury.backend.session.Region;
import app.accentury.backend.training.LearningVoiceSample;
import app.accentury.backend.training.LearningVoiceStore;
import app.accentury.backend.training.TrainingSample;
import app.accentury.backend.training.VoiceConsent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 억양 학습 채점 시도에 AI 결과를 적는다 (KAN-267, 명세서 §3.19) - 레벨테스트와 같은 전달 워커가 부른다.
 * <p>
 * 성공이면 원점수를 변환 점수로 바꾸고({@link IntonationScoring}) 올리고 내릴 음절을 JSON으로 함께 저장한다. 행이 결과의
 * 정본이라 폴링은 언제든 같은 본문을 받는다. 전이는 조건부 UPDATE라, 타임아웃 스위퍼나 탈퇴 파기가 먼저 닿은 시도에는
 * 0행으로 버려진다.
 * <p>
 * 로그에는 시도 id만 남긴다. 점수와 어절 글자는 남기지 않는다 (§2.6).
 */
@Component
public class IntonationAttemptLedger implements LearningAnalysisSink {

    private static final Logger log = LoggerFactory.getLogger(IntonationAttemptLedger.class);

    private final IntonationAttemptRepository attempts;
    private final IntonationScoring scoring;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LearningVoiceStore> voices;

    IntonationAttemptLedger(IntonationAttemptRepository attempts, IntonationScoring scoring,
                            ObjectMapper objectMapper, ObjectProvider<LearningVoiceStore> voices) {
        this.attempts = attempts;
        this.scoring = scoring;
        this.objectMapper = objectMapper;
        this.voices = voices;
    }

    @Override
    @Transactional
    public boolean start(String jobId) {
        return attempts.markStartedIfProcessing(jobId, now()) > 0;
    }

    @Override
    @Transactional
    public boolean complete(String jobId, AiAnalysisClient.Completed completed) {
        List<PitchFeedback> feedback = IntonationScoring.feedback(completed.orderSegments());
        int score = scoring.score(completed.intonationScore(), feedback);
        int updated = attempts.completeIfProcessing(jobId, completed.intonationScore(), score,
                objectMapper.writeValueAsString(feedback), completed.qualityCode(), completed.modelVersion(),
                completed.scoreVersion(), now());
        if (updated == 0) {
            log.warn("늦은 학습 채점 결과를 버린다 - 이미 종결됐거나 탈퇴로 삭제된 시도다 attemptId={}", jobId);
            return false;
        }
        log.info("학습 채점 완료 attemptId={} modelVersion={}", jobId, completed.modelVersion());
        return true;
    }

    @Override
    @Transactional
    public void fail(String jobId, AnalysisJobStatus failedStatus, String errorCode) {
        requireFailure(failedStatus);
        int updated = attempts.failAllIfProcessing(List.of(jobId), failedStatus, errorCode, now());
        if (updated == 0) {
            log.warn("늦은 학습 채점 실패 통지를 버린다 attemptId={} errorCode={}", jobId, errorCode);
        } else {
            log.info("학습 채점 실패 attemptId={} status={} errorCode={}", jobId, failedStatus, errorCode);
        }
    }

    @Override
    @Transactional
    public int failAll(Collection<String> jobIds, AnalysisJobStatus failedStatus, String errorCode) {
        requireFailure(failedStatus);
        if (jobIds.isEmpty()) {
            return 0;
        }
        int updated = attempts.failAllIfProcessing(jobIds, failedStatus, errorCode, now());
        log.info("학습 채점 일괄 종결 {}건 (요청 {}건) status={} errorCode={}",
                updated, jobIds.size(), failedStatus, errorCode);
        return updated;
    }

    /**
     * 음성 저장에 동의한 계정의 녹음만 음성 버킷 학습 트리에 남긴다 (§3.19). 성공과 계약대로 온 판정 실패를 남기고,
     * 계약 위반과 AI 불가({@code null})는 남기지 않는다 - 레벨테스트 음성과 같은 규칙이다.
     */
    @Override
    public void keepVoice(AnalysisDispatcher.AnalysisRequest request, AiAnalysisClient.@Nullable Outcome outcome,
                          String correlationId) {
        VoiceConsent consent = request.voiceConsent();
        if (consent == null) {
            return;
        }
        LearningVoiceStore store = voices.getIfAvailable(() -> LearningVoiceStore.NONE);
        String region = Region.forStorage(request.region()).name();
        String scriptKey = Objects.requireNonNull(request.scriptKey(), "학습 요청에는 scriptKey가 있다");
        LearningVoiceSample sample = switch (outcome) {
            case AiAnalysisClient.Completed completed -> new LearningVoiceSample(
                    request.analysisJobId(), request.itemId(), region, scriptKey, request.testVersion(),
                    request.scoreVersion(), request.durationMs(), TrainingSample.Outcome.COMPLETED,
                    completed.intonationScore(), completed.qualityCode(), completed.modelVersion(), null,
                    correlationId, consent, request.audio());
            case AiAnalysisClient.Rejected rejected when rejected.cause() == AiAnalysisClient.Rejected.Cause.JUDGED ->
                    new LearningVoiceSample(
                            request.analysisJobId(), request.itemId(), region, scriptKey, request.testVersion(),
                            request.scoreVersion(), request.durationMs(),
                            rejected.retryable() ? TrainingSample.Outcome.RETRYABLE_FAILED
                                    : TrainingSample.Outcome.FAILED,
                            null, null, null, rejected.errorCode(), correlationId, consent, request.audio());
            case AiAnalysisClient.Rejected ignored -> null;   // CONTRACT_VIOLATION
            case null -> null;
        };
        if (sample != null) {
            store.save(sample);
        }
    }

    private static void requireFailure(AnalysisJobStatus failedStatus) {
        if (failedStatus != AnalysisJobStatus.RETRYABLE_FAILED && failedStatus != AnalysisJobStatus.FAILED) {
            throw new IllegalArgumentException("실패 전이가 아니다: " + failedStatus);
        }
    }

    /** DB 열 정밀도(마이크로초)로 자른 현재 시각. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
