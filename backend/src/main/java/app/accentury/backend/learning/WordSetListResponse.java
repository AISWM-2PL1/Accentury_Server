package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /v0/learning/word-sets}의 200 응답 (명세서 §3.16, W-1).
 * <p>
 * 레지스트리는 발행본마다 진도가 빈 목록을 한 번 만들고, 계정마다 {@link #withProgress}로 완료 표시와 추천을 채운 사본을
 * 낸다 (KAN-268, {@link LearningProgress}).
 *
 * @param contentVersion 목록이 속한 발행본
 * @param sets           세트 요약 - seq 오름차순
 */
public record WordSetListResponse(String contentVersion, List<Summary> sets) {

    /**
     * 세트 요약 한 줄.
     *
     * @param completed             이 발행본에서 완료한 시도가 하나라도 있는지
     * @param latestAccuracyPercent 가장 최근에 완료한 시도의 정답률 - 완료한 적이 없으면 null
     * @param recommended           목록 안에서 많아야 하나만 true다 (§3.20)
     */
    public record Summary(String setId, int seq, int level, String category, String title,
                          int cardCount, int itemCount, boolean completed, @Nullable Integer latestAccuracyPercent,
                          boolean recommended) {

        /** 진도가 빈 요약 - 레지스트리가 발행본에서 만든다. */
        static Summary unstarted(String setId, int seq, int level, String category, String title, int cardCount,
                                 int itemCount) {
            return new Summary(setId, seq, level, category, title, cardCount, itemCount, false, null, false);
        }
    }

    /**
     * 계정의 진도를 채운 사본.
     *
     * @param latestAccuracyBySet 완료한 세트의 setId별 최근 정답률 - 키가 있으면 완료다
     * @param recommendedSetId    추천 세트 - 없으면 null
     */
    WordSetListResponse withProgress(Map<String, Integer> latestAccuracyBySet, @Nullable String recommendedSetId) {
        List<Summary> filled = sets.stream()
                .map(set -> {
                    Integer accuracy = latestAccuracyBySet.get(set.setId());
                    return new Summary(set.setId(), set.seq(), set.level(), set.category(), set.title(),
                            set.cardCount(), set.itemCount(), accuracy != null, accuracy,
                            set.setId().equals(recommendedSetId));
                })
                .toList();
        return new WordSetListResponse(contentVersion, filled);
    }
}
