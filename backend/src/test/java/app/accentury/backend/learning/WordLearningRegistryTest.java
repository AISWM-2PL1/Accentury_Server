package app.accentury.backend.learning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 발행 검증의 단위 명세 (KAN-265) - 틀린 발행본은 기동에서 막힌다. */
class WordLearningRegistryTest {

    @Test
    void 정상_발행본은_통과한다() {
        assertDoesNotThrow(() -> WordLearningRegistry.validate(definition(set("ws01", 1, 1, 2), set("ws02", 2, 5, 1))));
    }

    @Test
    void 레벨은_1부터_5다() {
        assertRejected(definition(set("ws01", 1, 0, 1)), "레벨");
        assertRejected(definition(set("ws01", 1, 6, 1)), "레벨");
    }

    @Test
    void 세트_seq는_1부터_연속이어야_한다() {
        assertRejected(definition(set("ws01", 1, 1, 1), set("ws02", 3, 1, 1)), "연속");
        assertRejected(definition(set("ws01", 1, 1, 1), set("ws02", 1, 1, 1)), "중복");
    }

    @Test
    void 카드_하나에_문항_하나다() {
        WordLearningDefinition.Set set = set("ws01", 1, 1, 2);
        List<WordLearningDefinition.Item> items = new ArrayList<>(set.items());
        items.remove(1);
        assertRejected(definition(withItems(set, items)), "카드 하나에 문항 하나");

        List<WordLearningDefinition.Item> doubled = new ArrayList<>(set.items());
        WordLearningDefinition.Item second = doubled.get(1);
        doubled.set(1, new WordLearningDefinition.Item(second.itemId(), 2, set.cards().get(0).cardId(),
                second.prompt(), second.choices(), second.correctChoiceId(), second.explanation()));
        assertRejected(definition(withItems(set, doubled)), "문항이 둘");
    }

    @Test
    void 정답은_보기_안에_있어야_하고_보기는_4개다() {
        WordLearningDefinition.Set set = set("ws01", 1, 1, 1);
        WordLearningDefinition.Item item = set.items().get(0);
        assertRejected(definition(withItems(set, List.of(new WordLearningDefinition.Item(item.itemId(), 1, item.cardId(),
                item.prompt(), item.choices(), "nope", item.explanation())))), "정답이 보기 안에 없다");
        assertRejected(definition(withItems(set, List.of(new WordLearningDefinition.Item(item.itemId(), 1, item.cardId(),
                item.prompt(), item.choices().subList(0, 3), item.correctChoiceId(), item.explanation())))), "4개");
        assertRejected(definition(withItems(set, List.of(new WordLearningDefinition.Item(item.itemId(), 1, item.cardId(),
                item.prompt(), item.choices(), item.correctChoiceId(), " ")))), "해설");
    }

    @Test
    void 식별자는_발행본_안에서_유일하다() {
        WordLearningDefinition.Set a = set("ws01", 1, 1, 1);
        WordLearningDefinition.Set b = set("ws01", 2, 1, 1);
        assertRejected(definition(a, b), "setId가 중복");
        WordLearningDefinition.Set c = set("ws02", 2, 1, 1);
        WordLearningDefinition.Set sameItems = new WordLearningDefinition.Set(c.setId(), c.seq(), c.level(), c.category(),
                c.title(), a.cards(), a.items());
        assertRejected(definition(a, sameItems), "중복");
    }

    @Test
    void 경남_발행본만_발행한다() {
        WordLearningDefinition definition = new WordLearningDefinition("wd-x", "GYEONGBUK", List.of(set("ws01", 1, 1, 1)));
        assertRejected(definition, "경남");
    }

    @Test
    void 정답률은_반올림한_정수다() {
        assertEquals(67, WordAttemptResultResponse.accuracyPercent(2, 3));
        assertEquals(33, WordAttemptResultResponse.accuracyPercent(1, 3));
        assertEquals(100, WordAttemptResultResponse.accuracyPercent(7, 7));
        assertEquals(0, WordAttemptResultResponse.accuracyPercent(0, 7));
    }

    private static void assertRejected(WordLearningDefinition definition, String messagePart) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> WordLearningRegistry.validate(definition));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    private static WordLearningDefinition definition(WordLearningDefinition.Set... sets) {
        return new WordLearningDefinition("wd-test", "GYEONGNAM", List.of(sets));
    }

    private static WordLearningDefinition.Set withItems(WordLearningDefinition.Set set,
                                                        List<WordLearningDefinition.Item> items) {
        return new WordLearningDefinition.Set(set.setId(), set.seq(), set.level(), set.category(), set.title(),
                set.cards(), items);
    }

    private static WordLearningDefinition.Set set(String setId, int seq, int level, int cardCount) {
        List<WordLearningDefinition.Card> cards = new ArrayList<>();
        List<WordLearningDefinition.Item> items = new ArrayList<>();
        for (int i = 1; i <= cardCount; i++) {
            String cardId = setId + "c" + i;
            String itemId = setId + "q" + i;
            cards.add(new WordLearningDefinition.Card(cardId, "표준" + i, "사투리" + i));
            List<WordLearningDefinition.Choice> choices = List.of(
                    new WordLearningDefinition.Choice(itemId + "a", "사투리" + i),
                    new WordLearningDefinition.Choice(itemId + "b", "오답1"),
                    new WordLearningDefinition.Choice(itemId + "c", "오답2"),
                    new WordLearningDefinition.Choice(itemId + "d", "오답3"));
            items.add(new WordLearningDefinition.Item(itemId, i, cardId, "'표준" + i + "'를 경남 사투리로?",
                    choices, itemId + "a", "'사투리" + i + "'는 경남에서 '표준" + i + "'를 이르는 말입니다."));
        }
        return new WordLearningDefinition.Set(setId, seq, level, "분류", "분류 " + seq, cards, items);
    }
}
