package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /v0/learning/intonation-courses}의 200 응답 (명세서 §3.17, A-1).
 * <p>
 * 레지스트리는 발행본마다 진도가 빈 목록을 한 번 만들고, 계정마다 {@link #withProgress}로 완료 표시와 추천을 채운 사본을
 * 낸다 (KAN-268, {@link LearningProgress}). 코스가 없으면 빈 배열이다.
 *
 * @param contentVersion 목록이 속한 발행본
 * @param courses        코스 요약 - seq 오름차순
 */
public record IntonationCourseListResponse(String contentVersion, List<Summary> courses) {

    /**
     * 코스 요약 한 줄. {@code topic}은 값이 없으면 null이다.
     *
     * @param completedCardCount 채점이 {@code COMPLETED}로 끝난 시도가 하나라도 있는 카드 수 - 점수와 무관하다
     * @param completed          모든 카드가 완료됐는지
     * @param recommended        목록 안에서 많아야 하나만 true다 (§3.20)
     * @param score              세트 점수 (KAN-359) - 카드별 최고 점수의 평균을 반올림한 0~100, 점수가 없는 카드가 있으면 null
     * @param stars              세트 점수의 별 0~3 ({@link LearningStars#stars}) - 완료하지 않은 코스는 0이다
     */
    public record Summary(String courseId, int seq, int level, @Nullable String topic, String title,
                          int cardCount, int completedCardCount, boolean completed, boolean recommended,
                          @Nullable Integer score, int stars) {

        /** 진도가 빈 요약 - 레지스트리가 발행본에서 만든다. */
        static Summary unstarted(String courseId, int seq, int level, @Nullable String topic, String title,
                                 int cardCount) {
            return new Summary(courseId, seq, level, topic, title, cardCount, 0, false, false, null,
                    LearningStars.stars(null));
        }
    }

    /**
     * 계정의 진도를 채운 사본.
     *
     * @param completedCardsByCourse courseId별 완료 카드 수 - 없는 코스는 0이다
     * @param scoreByCourse          courseId별 세트 점수 - 점수가 없는 코스는 키가 없다
     * @param recommendedCourseId    추천 코스 - 없으면 null
     */
    IntonationCourseListResponse withProgress(Map<String, Integer> completedCardsByCourse,
                                              Map<String, Integer> scoreByCourse,
                                              @Nullable String recommendedCourseId) {
        List<Summary> filled = courses.stream()
                .map(course -> {
                    int done = completedCardsByCourse.getOrDefault(course.courseId(), 0);
                    Integer score = scoreByCourse.get(course.courseId());
                    return new Summary(course.courseId(), course.seq(), course.level(), course.topic(), course.title(),
                            course.cardCount(), done, done == course.cardCount(),
                            course.courseId().equals(recommendedCourseId), score, LearningStars.stars(score));
                })
                .toList();
        return new IntonationCourseListResponse(contentVersion, filled);
    }
}
