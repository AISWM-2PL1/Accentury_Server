package app.accentury.backend.learning;

import java.util.List;

/**
 * {@code GET /v0/learning/levels}의 200 응답 (KAN-359, 명세서 §3.20 구간 요약) - 앱 학습 탭의 사투리 여행 지도가 쓴다.
 * <p>
 * 구간은 코스 목록(§3.17)과 세트 목록(§3.16)의 {@code level}이고, 세트는 억양 코스와 단어 세트를 함께 이른다. 값은 전부
 * 두 목록과 같은 계산({@link LearningProgress})에서 나온다. {@code unlocked}는 표시용이다 - 서버는 잠긴 구간의 시도
 * 시작을 막지 않는다.
 *
 * @param totalStars     현재 발행본 전체에서 모은 별 - 구간별 {@code collectedStars}의 합이자 두 목록의 {@code stars} 합
 * @param starThresholds 별 경계값 ({@link LearningStars#THRESHOLDS}) - 앱이 결과 화면의 「이번 스탬프」 별을 그리는 데 쓴다
 * @param levels         구간 - level 오름차순
 */
public record LearningLevelsResponse(int totalStars, List<Integer> starThresholds, List<Level> levels) {

    /**
     * 구간 하나.
     *
     * @param setCount          이 구간의 억양 코스 수 + 단어 세트 수
     * @param completedSetCount 그 가운데 목록에서 {@code completed}인 수
     * @param collectedStars    이 구간 세트들의 별 합 - {@code requiredStars}를 넘을 수 있다
     * @param requiredStars     다음 구간이 열리는 별 수 ({@link LearningStars#requiredStars})
     * @param unlocked          가장 낮은 구간은 늘 true, 그 밖은 바로 앞 구간이 별을 필요한 만큼 모았으면 true
     */
    public record Level(int level, int setCount, int completedSetCount, int collectedStars, int requiredStars,
                        boolean unlocked) {
    }
}
