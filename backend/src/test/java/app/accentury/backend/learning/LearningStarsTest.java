package app.accentury.backend.learning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 별 정책 (KAN-359, 명세서 §3.20 구간 요약) - DB 없이 숫자와 규칙만 고정한다. */
class LearningStarsTest {

    @Test
    void 별_구간은_0에서_29가_1개_30에서_69가_2개_70에서_100이_3개다() {
        assertEquals(1, LearningStars.stars(0), "완료한 세트는 0점이어도 별 1개다");
        assertEquals(1, LearningStars.stars(29));
        assertEquals(2, LearningStars.stars(30));
        assertEquals(2, LearningStars.stars(69));
        assertEquals(3, LearningStars.stars(70));
        assertEquals(3, LearningStars.stars(100));
    }

    @Test
    void 완료하지_않은_세트는_별이_0개다() {
        assertEquals(0, LearningStars.stars(null));
    }

    @Test
    void 앱에_내려주는_경계값은_30과_70이다() {
        assertEquals(List.of(30, 70), LearningStars.THRESHOLDS);
    }

    @Test
    void 필요_별은_세트_수의_두_배에_세트_수를_3으로_나눈_몫을_더한다() {
        assertEquals(21, LearningStars.requiredStars(9));
        // 나눗셈은 내림이다.
        assertEquals(16, LearningStars.requiredStars(7));
        assertEquals(18, LearningStars.requiredStars(8));
        assertEquals(0, LearningStars.requiredStars(0));
    }

    @Test
    void 다음_구간은_별이_필요_별에_닿는_순간_열린다() {
        assertFalse(LearningStars.unlocksNext(20, 9));
        assertTrue(LearningStars.unlocksNext(21, 9));
        assertTrue(LearningStars.unlocksNext(27, 9));
    }

    @Test
    void 최고_기록은_점수가_내려가도_유지된다() {
        assertEquals(80, LearningStars.best(null, 80));
        assertEquals(80, LearningStars.best(80, 40));
        assertEquals(90, LearningStars.best(80, 90));
        assertEquals(0, LearningStars.best(null, 0));
    }

    @Test
    void 억양_세트_점수는_카드별_최고_점수의_평균을_반올림한다() {
        List<String> cards = List.of("a", "b", "c");
        assertEquals(70, LearningStars.intonationSetScore(cards, Map.of("a", 70, "b", 70, "c", 71)));
        assertEquals(71, LearningStars.intonationSetScore(cards, Map.of("a", 70, "b", 71, "c", 71)));
        assertEquals(100, LearningStars.intonationSetScore(cards, Map.of("a", 100, "b", 100, "c", 100)));
        assertEquals(0, LearningStars.intonationSetScore(cards, Map.of("a", 0, "b", 0, "c", 0)));
        // 0.5는 올림이다.
        assertEquals(71, LearningStars.intonationSetScore(List.of("a", "b"), Map.of("a", 70, "b", 71)));
    }

    @Test
    void 점수가_없는_카드가_있으면_억양_세트_점수는_null이다() {
        assertNull(LearningStars.intonationSetScore(List.of("a", "b", "c"), Map.of("a", 70, "b", 70)));
        assertNull(LearningStars.intonationSetScore(List.of(), Map.of()));
    }
}
