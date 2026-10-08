package app.accentury.backend.learning;

import app.accentury.backend.testdefinition.TestDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@code GET /v0/learning/intonation-courses/{courseId}}의 200 응답 (명세서 §3.17, A-2).
 * <p>
 * <b>{@code scriptKey}가 없다.</b> AI 채점용 대사 식별자는 서버 안에서만 쓰고(KAN-267이 {@code cardId}로 찾는다),
 * 기준 음원은 발행본의 상대 경로 대신 완성 URL로 준다. {@link IntonationLearningDefinition.Card}를 그대로 직렬화하지
 * 않는 이유다.
 */
public record IntonationCourseResponse(String contentVersion, String courseId, int seq, int level,
                                       @Nullable String topic, String title, List<Card> cards) {

    /** 공개 카드. */
    public record Card(String cardId, int seq, @Nullable String standard, String dialect,
                       TestDefinition.GuideF0 guideF0, @Nullable String referenceAudioUrl) {
    }

    /**
     * 발행본의 코스를 공개 모양으로 바꾼다. 기준 음원 URL은 지금 늘 null이다 - 기준 URL 설정이 아직 없고, 경로가
     * 있는 카드는 발행 검증({@link IntonationLearningRegistry#validate})이 막는다. 첫 음원을 발행할 때 기준 URL과
     * 합치는 자리가 여기다 (§3.17, KAN-132의 등급 이미지와 같은 방식).
     */
    static IntonationCourseResponse from(String contentVersion, IntonationLearningDefinition.Course course) {
        List<Card> cards = course.cards().stream()
                .map(card -> new Card(card.cardId(), card.seq(), card.standard(), card.dialect(), card.guideF0(),
                        null))
                .toList();
        return new IntonationCourseResponse(contentVersion, course.courseId(), course.seq(), course.level(),
                course.topic(), course.title(), cards);
    }
}
