package app.accentury.backend.learning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 추천 규칙 (KAN-268, 명세서 §3.20, 2026-10-11 결정) - DB 없이 규칙만 본다. */
class LearningProgressTest {

    private record Item(String id, int level) {
    }

    /** seq 순서의 목록 - 레벨이 seq와 꼭 같은 순서는 아니다 (발행본의 코스는 레벨 순, 세트는 분류 순). */
    private static final List<Item> ITEMS = List.of(
            new Item("a", 2), new Item("b", 1), new Item("c", 3), new Item("d", 1), new Item("e", 3), new Item("f", 5));

    @Test
    void 등급_기록이_없으면_가장_낮은_레벨의_첫_항목이다() {
        assertEquals("b", pick(Set.of(), null));
    }

    @Test
    void 등급이_있으면_그_레벨의_첫_미완료_항목이다() {
        assertEquals("c", pick(Set.of(), 3));
        assertEquals("e", pick(Set.of("c"), 3));
    }

    @Test
    void 기준_레벨을_다_끝내면_다음_레벨로_가고_낮은_레벨로는_돌아가지_않는다() {
        assertEquals("f", pick(Set.of("c", "e"), 3));
        // 레벨 4 항목이 없으면 그 위 레벨에서 고른다.
        assertEquals("f", pick(Set.of(), 4));
        // 기록이 없을 때도 가장 낮은 레벨을 다 끝내면 다음 레벨이다.
        assertEquals("a", pick(Set.of("b", "d"), null));
    }

    @Test
    void 고를_것이_없으면_null이다() {
        assertNull(pick(Set.of("f"), 5));
        assertNull(pick(Set.of("a", "b", "c", "d", "e", "f"), null));
        assertNull(LearningProgress.recommend(List.<Item>of(), Item::level, item -> false, 1));
    }

    private static String pick(Set<String> completed, Integer rank) {
        Item item = LearningProgress.recommend(ITEMS, Item::level, candidate -> completed.contains(candidate.id()), rank);
        return item == null ? null : item.id();
    }
}
