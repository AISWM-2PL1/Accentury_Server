package app.accentury.backend.learning;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.analysis.AiAnalysisClient;
import app.accentury.backend.scoring.ScorePolicyRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 억양 학습 점수 규칙 (KAN-267, 명세서 §3.19, 2026-10-10 결정) - 기본 설정(sv-0.5, 기준 90)으로 본다.
 * sv-0.5의 억양 전처리는 구간 폭 5, 구간당 5% 감소라 계수는 ceil(원점수 / 5) x 5 %다.
 */
class IntonationScoringTest {

    private final IntonationScoring scoring = new IntonationScoring(
            new ScorePolicyRegistry(JsonMapper.builder().build()), PropertiesFixture.defaults());

    private static final List<PitchFeedback> NONE = List.of();
    private static final List<PitchFeedback> ONE_SYLLABLE = List.of(new PitchFeedback("끓일라고", "끓", "라", false));

    @Test
    void 원점수는_레벨테스트와_같은_구간_계수로_바뀐다() {
        // 피드백이 있어 100점 처리가 걸리지 않는 조건에서 변환만 본다.
        assertEquals(90, scoring.score(95, ONE_SYLLABLE), "95 x 95% = 90.25");
        assertEquals(81, scoring.score(90, ONE_SYLLABLE), "90 x 90% = 81");
        assertEquals(96, scoring.score(96, ONE_SYLLABLE), "96은 최상위 구간이라 계수 100%");
        assertEquals(100, scoring.score(100, ONE_SYLLABLE));
        assertEquals(0, scoring.score(0, ONE_SYLLABLE));
        assertEquals(0, scoring.score(5, ONE_SYLLABLE), "5 x 5% = 0.25 -> 사사오입 0");
    }

    @Test
    void 변환_점수가_기준_이상이고_보여_줄_음절이_없으면_100점이다() {
        assertEquals(100, scoring.score(95, NONE), "변환 90 = 기준이라 100");
        assertEquals(100, scoring.score(95, List.of(new PitchFeedback("가마솥에", null, null, true))),
                "갈리는 어절만 있으면 보여 줄 음절이 없다");
        assertEquals(100, scoring.score(95, List.of(new PitchFeedback("가마솥에", null, null, false))),
                "가장 높인 음절이 같은 어절도 보여 줄 음절이 없다");
        assertEquals(90, scoring.score(95, ONE_SYLLABLE), "올릴 음절이 있으면 그대로");
        assertEquals(89, scoring.score(94, NONE), "94 x 95% = 89.3 -> 89, 기준 미만이면 피드백이 없어도 그대로");
    }

    @Test
    void order_항목은_글자_그대로_옮기고_refAgree가_0_6_미만이면_갈리는_어절로_두_음절을_비운다() {
        List<PitchFeedback> feedback = IntonationScoring.feedback(List.of(
                new AiAnalysisClient.OrderSegment("끓일라고", "끓", "라", 0.75),
                new AiAnalysisClient.OrderSegment("가마솥에", "가", "솥", 0.59),
                new AiAnalysisClient.OrderSegment("부우면", null, null, 0.6)));

        assertEquals(List.of(
                new PitchFeedback("끓일라고", "끓", "라", false),
                new PitchFeedback("가마솥에", null, null, true),
                new PitchFeedback("부우면", null, null, false)), feedback);
    }
}
