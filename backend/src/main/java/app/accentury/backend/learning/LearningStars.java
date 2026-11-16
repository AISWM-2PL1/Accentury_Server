package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 별 모으기 정책 (KAN-359, 명세서 §3.16, §3.17, §3.20 구간 요약) - 숫자와 규칙은 여기에만 둔다.
 * <p>
 * 세트(억양 코스 또는 단어 세트)를 마치면 세트 점수(0~100)의 구간에 따라 별을 받고, 구간(level)마다 별을 필요한 만큼
 * 모으면 다음 구간이 열린다. 재도전은 최고 기록을 유지한다 - 세트 점수는 지금까지의 최고 값이라 다시 풀어 점수가 내려가도
 * 별이 줄지 않고, 그래서 한 번 열린 구간은 다시 잠기지 않는다. 별과 세트 점수는 저장하지 않고 조회 때 시도 기록에서
 * 계산하므로({@link LearningProgress}) 이 값들을 바꿔도 마이그레이션이 없다.
 */
final class LearningStars {

    /** 별 2개가 시작되는 세트 점수. */
    private static final int TWO_STARS_FROM = 30;

    /** 별 3개가 시작되는 세트 점수. */
    private static final int THREE_STARS_FROM = 70;

    /** 필요 별에서 세트 하나가 기본으로 요구하는 별 수. */
    private static final int REQUIRED_PER_SET = 2;

    /** 필요 별에서 이 수의 세트마다 별 하나를 더 요구한다. */
    private static final int EXTRA_STAR_EVERY_SETS = 3;

    /**
     * 앱에 내려주는 별 경계값 (§3.20 {@code starThresholds}) - 점수가 첫 값 미만이면 별 1개, 둘째 값 미만이면 2개, 그
     * 이상이면 3개다.
     */
    static final List<Integer> THRESHOLDS = List.of(TWO_STARS_FROM, THREE_STARS_FROM);

    private LearningStars() {
    }

    /**
     * 세트 점수의 별. 완료하지 않은 세트(점수 null)는 0개이고, 완료한 세트는 0점이어도 1개다.
     *
     * @param score 세트 점수 (0~100) - 완료하지 않았으면 null
     */
    static int stars(@Nullable Integer score) {
        if (score == null) {
            return 0;
        }
        if (score < TWO_STARS_FROM) {
            return 1;
        }
        return score < THREE_STARS_FROM ? 2 : 3;
    }

    /** 다음 구간이 열리는 별 수 - 세트 수 x 2 + (세트 수 / 3의 내림). 세트가 9개면 21개다. */
    static int requiredStars(int setCount) {
        return setCount * REQUIRED_PER_SET + setCount / EXTRA_STAR_EVERY_SETS;
    }

    /** 다음 구간이 열리는지 - 별만 본다. 세트를 모두 완료해도 별이 모자라면 열리지 않는다. */
    static boolean unlocksNext(int collectedStars, int setCount) {
        return collectedStars >= requiredStars(setCount);
    }

    /**
     * 최고 기록 유지 - 지금까지의 최고 점수와 새 점수 가운데 높은 쪽. 카드별 최고 점수와 단어 세트 점수를 접을 때 쓴다.
     *
     * @param best 지금까지의 최고 점수 - 아직 없으면 null
     */
    static int best(@Nullable Integer best, int score) {
        return best == null ? score : Math.max(best, score);
    }

    /**
     * 억양 세트 점수 - 카드별 최고 점수의 평균을 반올림한 정수(0.5는 올림). 카드별 최고는 회차와 날짜가 달라도 합친 값이다.
     * 점수가 없는 카드가 하나라도 있으면(세트 미완료) null이다.
     *
     * @param cardIds         세트의 카드
     * @param bestScoreByCard 카드별 최고 점수 - 채점을 마친 적이 없는 카드는 키가 없다
     */
    static @Nullable Integer intonationSetScore(Collection<String> cardIds, Map<String, Integer> bestScoreByCard) {
        if (cardIds.isEmpty()) {
            return null;
        }
        int sum = 0;
        for (String cardId : cardIds) {
            Integer best = bestScoreByCard.get(cardId);
            if (best == null) {
                return null;
            }
            sum += best;
        }
        // 정수 나눗셈으로 반올림한다 - (2 x 합 + 장수) / (2 x 장수)는 합 / 장수의 0.5 올림이다.
        return (2 * sum + cardIds.size()) / (2 * cardIds.size());
    }
}
