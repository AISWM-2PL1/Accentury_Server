package app.accentury.backend.learning;

import app.accentury.backend.testdefinition.TestDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 억양 학습 발행 검증과 기동 동작의 단위 명세 (KAN-264) - 틀린 발행본은 기동에서 막힌다. */
class IntonationLearningRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TestDefinition.GuideF0 CURVE =
            new TestDefinition.GuideF0("semitone", 22, java.util.Arrays.asList(1.0, null, 2.0), null, null);

    @Test
    void 정상_발행본은_통과한다() {
        assertDoesNotThrow(() -> IntonationLearningRegistry.validate(
                definition(course("ic01", 1, 1, 2), course("ic02", 2, 5, 1))));
    }

    @Test
    void 코스가_없는_발행본은_빈_목록으로_통과한다() {
        assertDoesNotThrow(() -> IntonationLearningRegistry.validate(definition()));
    }

    @Test
    void 레벨은_1부터_5다() {
        assertRejected(definition(course("ic01", 1, 0, 1)), "레벨");
        assertRejected(definition(course("ic01", 1, 6, 1)), "레벨");
    }

    @Test
    void 코스와_카드_seq는_1부터_연속이어야_한다() {
        assertRejected(definition(course("ic01", 1, 1, 1), course("ic02", 3, 1, 1)), "연속");
        IntonationLearningDefinition.Course course = course("ic01", 1, 1, 2);
        IntonationLearningDefinition.Card second = course.cards().get(1);
        assertRejected(definition(withCards(course, List.of(course.cards().get(0), card(second, 3, second.scriptKey(),
                second.guideF0(), null)))), "연속");
    }

    @Test
    void 식별자와_scriptKey는_발행본_안에서_유일하다() {
        IntonationLearningDefinition.Course a = course("ic01", 1, 1, 1);
        assertRejected(definition(a, course("ic01", 2, 1, 1)), "courseId가 중복");
        IntonationLearningDefinition.Course b = course("ic02", 2, 1, 1);
        IntonationLearningDefinition.Card only = b.cards().get(0);
        assertRejected(definition(a, withCards(b, List.of(card(only, 1, a.cards().get(0).scriptKey(),
                only.guideF0(), null)))), "scriptKey가 중복");
    }

    @Test
    void 카드에는_scriptKey와_곡선이_있어야_한다() {
        IntonationLearningDefinition.Course course = course("ic01", 1, 1, 1);
        IntonationLearningDefinition.Card only = course.cards().get(0);
        assertRejected(definition(withCards(course, List.of(card(only, 1, " ", only.guideF0(), null)))), "scriptKey");
        assertRejected(definition(withCards(course, List.of(card(only, 1, only.scriptKey(),
                new TestDefinition.GuideF0("semitone", 22, List.of(), null, null), null)))), "values");
        assertRejected(definition(withCards(course, List.of(card(only, 1, only.scriptKey(),
                new TestDefinition.GuideF0("semitone", 22, List.of(1.0, 2.0), List.of(0.0, 1.0), null), null)))),
                "밴드");
    }

    @Test
    void 기준_음원_경로는_기준_URL_설정_전에는_실을_수_없다() {
        IntonationLearningDefinition.Course course = course("ic01", 1, 1, 1);
        IntonationLearningDefinition.Card only = course.cards().get(0);
        assertRejected(definition(withCards(course, List.of(card(only, 1, only.scriptKey(), only.guideF0(),
                "audio/ic01c1.m4a")))), "기준 URL");
    }

    @Test
    void 경남_발행본만_발행한다() {
        assertRejected(new IntonationLearningDefinition("in-x", "GYEONGBUK", List.of(course("ic01", 1, 1, 1))), "경남");
    }

    // === 기동 동작 (PR #31 리뷰) - 저장소를 가짜로 바꿔 DB 없이 생성자를 돌린다 ===

    @Test
    void 발행본이_하나도_없으면_기동이_막힌다() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new IntonationLearningRegistry(MAPPER, List::of));
        assertTrue(e.getMessage().contains("하나도 없다"), e.getMessage());
    }

    @Test
    void 목록과_상세는_발행_시각이_가장_늦은_발행본을_쓴다() {
        // 저장소는 발행 시각 오름차순으로 돌려준다 - 마지막 행이 현재 발행본이다.
        IntonationLearningRegistry registry = new IntonationLearningRegistry(MAPPER, () -> List.of(
                stored(new IntonationLearningDefinition("in-old", "GYEONGNAM", List.of(course("ic01", 1, 1, 1))),
                        "2026-10-01T00:00:00Z"),
                stored(new IntonationLearningDefinition("in-new", "GYEONGNAM", List.of(course("ic01", 1, 2, 2))),
                        "2026-10-08T00:00:00Z")));

        assertEquals("in-new", registry.current().definition().contentVersion());
        assertEquals("in-new", registry.current().listResponse().contentVersion());
        assertEquals(2, registry.current().courseResponse("ic01").cards().size());
    }

    @Test
    void 코스가_없는_발행본의_목록은_빈_배열로_나간다() {
        IntonationLearningRegistry registry = new IntonationLearningRegistry(MAPPER, () -> List.of(
                stored(new IntonationLearningDefinition("in-empty", "GYEONGNAM", List.of()), "2026-10-08T00:00:00Z")));

        JsonNode list = MAPPER.readTree(MAPPER.writeValueAsString(registry.current().listResponse()));
        assertTrue(list.get("courses").isArray(), list.toString());
        assertEquals(0, list.get("courses").size());
    }

    private static StoredIntonationLearningDefinition stored(IntonationLearningDefinition definition,
                                                             String publishedAt) {
        return new StoredIntonationLearningDefinition(definition.contentVersion(), definition.dialect(),
                MAPPER.writeValueAsString(definition), Instant.parse(publishedAt));
    }

    private static void assertRejected(IntonationLearningDefinition definition, String messagePart) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IntonationLearningRegistry.validate(definition));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    private static IntonationLearningDefinition definition(IntonationLearningDefinition.Course... courses) {
        return new IntonationLearningDefinition("in-test", "GYEONGNAM", List.of(courses));
    }

    private static IntonationLearningDefinition.Course withCards(IntonationLearningDefinition.Course course,
                                                                 List<IntonationLearningDefinition.Card> cards) {
        return new IntonationLearningDefinition.Course(course.courseId(), course.seq(), course.level(), course.topic(),
                course.title(), cards);
    }

    private static IntonationLearningDefinition.Card card(IntonationLearningDefinition.Card base, int seq,
                                                          String scriptKey, TestDefinition.GuideF0 guideF0,
                                                          String referenceAudioPath) {
        return new IntonationLearningDefinition.Card(base.cardId(), seq, base.standard(), base.dialect(), scriptKey,
                guideF0, referenceAudioPath);
    }

    private static IntonationLearningDefinition.Course course(String courseId, int seq, int level, int cardCount) {
        List<IntonationLearningDefinition.Card> cards = new ArrayList<>();
        for (int i = 1; i <= cardCount; i++) {
            cards.add(new IntonationLearningDefinition.Card(courseId + "c" + i, i, null, "사투리 대사 " + i,
                    courseId + "|" + i, CURVE, null));
        }
        return new IntonationLearningDefinition.Course(courseId, seq, level, null, "코스 " + seq, cards);
    }
}
