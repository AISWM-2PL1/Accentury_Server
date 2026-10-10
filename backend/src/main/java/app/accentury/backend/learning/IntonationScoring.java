package app.accentury.backend.learning;

import app.accentury.backend.analysis.AiAnalysisClient;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.scoring.ScorePolicy;
import app.accentury.backend.scoring.ScorePolicyRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 억양 학습 점수 규칙 (KAN-267, 명세서 §3.19, 2026-10-10 결정) - 순수 산술이고 상태가 없다.
 * <ul>
 *   <li>변환 점수 = AI 원점수에 레벨테스트와 같은 억양 전처리({@link ScorePolicy#intonationCoefficientPercent})를
 *       문항 1개로 적용해 사사오입한 정수다. 원점수 95는 계수 95%로 90.25가 되어 90이다. 집계({@code ScoreAggregator})와
 *       같은 정수 산술이라 플랫폼과 무관하게 같은 값이다.</li>
 *   <li>100점 처리 - 변환 점수가 기준 이상이고 올리고 내릴 음절이 하나도 없으면 100이다. 학습에만 있고 레벨테스트
 *       집계에는 없다.</li>
 *   <li>올리고 내릴 음절 - 모델 order 항목을 글자 그대로 옮기고, {@code refAgree}가 0.6 미만이면 경남 화자끼리 갈리는
 *       어절({@code split})로 표시한다.</li>
 * </ul>
 */
@Component
class IntonationScoring {

    /**
     * 경남 화자끼리 갈리는 어절의 기준 - 모델이 이 값 미만에서 raise와 lower를 null로 준다 (ai-model 26ac67a의
     * {@code serve.py}). 모델 쪽 값이 바뀌면 같이 바꾼다.
     */
    static final double SPLIT_REF_AGREE = 0.6;

    private final ScorePolicy policy;
    private final int perfectScoreThreshold;

    IntonationScoring(ScorePolicyRegistry policies, AccenturyProperties properties) {
        String scoreVersion = properties.learning().intonationScoreVersion();
        if (!policies.isPublished(scoreVersion)) {
            throw new IllegalStateException("accentury.learning.intonation-score-version(" + scoreVersion
                    + ")이 발행된 점수 버전이 아니다 - score-versions seed에 있는 값이어야 한다");
        }
        int threshold = properties.learning().perfectScoreThreshold();
        if (threshold < 0 || threshold > 100) {
            throw new IllegalStateException("accentury.learning.perfect-score-threshold는 0~100이어야 한다: " + threshold);
        }
        this.policy = policies.get(scoreVersion);
        this.perfectScoreThreshold = threshold;
    }

    /** AI 요청과 시도 기록에 싣는 점수 버전. */
    String scoreVersion() {
        return policy.scoreVersion();
    }

    /** 사용자에게 보이는 점수 - 변환 뒤 100점 처리까지 한 값이다. */
    int score(int rawScore, List<PitchFeedback> feedback) {
        long coefficientPercent = policy.intonationCoefficientPercent(rawScore, 1);
        // 사사오입 정수 나눗셈 - ScoreAggregator.roundHalfUp과 같은 식이다 (raw x 계수% / 100).
        int converted = (int) ((2L * rawScore * coefficientPercent + 100) / 200);
        if (converted >= perfectScoreThreshold && feedback.stream().noneMatch(PitchFeedback::hasSyllable)) {
            return 100;
        }
        return converted;
    }

    /** 모델 order 항목을 응답 모양으로 옮긴다. 갈리는 어절은 모델 값과 상관없이 두 음절을 비운다 (§3.19). */
    static List<PitchFeedback> feedback(List<AiAnalysisClient.OrderSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    boolean split = segment.refAgree() < SPLIT_REF_AGREE;
                    return new PitchFeedback(segment.word(), split ? null : segment.raise(),
                            split ? null : segment.lower(), split);
                })
                .toList();
    }
}
