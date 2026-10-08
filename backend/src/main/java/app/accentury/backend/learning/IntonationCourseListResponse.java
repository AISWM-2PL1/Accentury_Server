package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@code GET /v0/learning/intonation-courses}의 200 응답 (명세서 §3.17, A-1).
 * <p>
 * 완료 표시와 추천은 아직 없다 - 학습 진도 티켓(KAN-268)이 필드를 더한다. 코스가 없으면 빈 배열이다.
 *
 * @param contentVersion 목록이 속한 발행본
 * @param courses        코스 요약 - seq 오름차순
 */
public record IntonationCourseListResponse(String contentVersion, List<Summary> courses) {

    /** 코스 요약 한 줄. {@code topic}은 값이 없으면 null이다. */
    public record Summary(String courseId, int seq, int level, @Nullable String topic, String title,
                          int cardCount) {
    }
}
