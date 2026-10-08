package app.accentury.backend.learning;

import java.util.List;

/**
 * {@code GET /v0/learning/word-sets/{setId}}의 200 응답 (명세서 §3.16, W-2와 W-3).
 * <p>
 * <b>정답과 해설이 없다.</b> 문항의 {@code correctChoiceId}와 {@code explanation}은 제출 응답({@link WordAnswerResponse})에서만
 * 준다. {@link WordLearningDefinition.Item}을 그대로 직렬화하면 정답이 새므로 여기서 모양을 따로 둔다.
 */
public record WordSetResponse(String contentVersion, String setId, int seq, int level, String category, String title,
                              List<WordLearningDefinition.Card> cards, List<Item> items) {

    /** 정답을 뺀 문항. */
    public record Item(String itemId, int seq, String cardId, String prompt,
                       List<WordLearningDefinition.Choice> choices) {
    }

    static WordSetResponse from(String contentVersion, WordLearningDefinition.Set set) {
        List<Item> items = set.items().stream()
                .map(item -> new Item(item.itemId(), item.seq(), item.cardId(), item.prompt(), item.choices()))
                .toList();
        return new WordSetResponse(contentVersion, set.setId(), set.seq(), set.level(), set.category(),
                set.title(), set.cards(), items);
    }
}
