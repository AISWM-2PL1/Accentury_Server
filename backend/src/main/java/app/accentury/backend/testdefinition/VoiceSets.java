package app.accentury.backend.testdefinition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 문항 풀을 세트로 나누는 규칙 (KAN-182, 2026-09-01 확정 · 2026-09-04 어휘 풀 확장).
 * <p>
 * 세트는 발행본에 손으로 나열하지 않고 여기서 유도한다 - 풀 크기가 바뀌어도 규칙만 따르면
 * 되고, 사람이 나누다 빠뜨리거나 겹치는 실수가 없다. 규칙은 순수 산술이라 같은 풀이면
 * 어느 인스턴스에서 유도해도 같은 세트다 (다중 인스턴스에서 세트 번호가 어긋나지 않는다).
 * <p>
 * 풀은 <b>둘</b>이다 - 음성 풀(N개)과 어휘 풀(M개). 세트 하나는 음성 풀에서 v개, 어휘 풀에서
 * w개를 가져온다. v와 w, 출제 순서는 발행본의 {@link SetLayout}이 정한다 (KAN-260, 2026-10-04) -
 * 필드가 없는 발행본은 v = w = 5, 교차다. 아래 규칙의 5는 그 기본값이다.
 * 어휘도 나누는 것은 2026-09-04 결정이다: 세트가 29개인데 어휘 5문항이 고정이면 어느 세트를
 * 응시하든 같은 어휘를 본다. 클래스와 API 이름이 {@code voiceSet}인 것은 KAN-182가 이미
 * 명세와 클라이언트에 그 이름으로 나간 뒤라서다 - 지금은 음성만이 아니라 세트 번호 자체를
 * 가리킨다.
 * <ul>
 *   <li>풀 = 정의의 VOICE 문항 목록과 VOCABULARY 문항 목록, 각각 seq 오름차순이 풀 순서다
 *       (poolIndex 1..N). 발행본의 배열 순서가 아니라 seq를 쓰는 것은 레지스트리가 모든 순서를
 *       seq로 고정하기 때문이다 (KAN-10 AC - 배열 순서에 의존하지 않는다).</li>
 *   <li>세트 수 = max(ceil(N / v), ceil(M / w)) - v = w = 5면 ceil(max(N, M) / 5)와 같다. 더 많은
 *       세트가 필요한 쪽을 기준으로 잡아야 그 풀의 문항이 어느 세트에도 실리지 못하고 남는 일이
 *       없다.</li>
 *   <li>세트 k가 어느 풀에서 가져오는 자리는 (k-1)*5부터 5칸이고, 풀 크기를 넘으면 풀의
 *       처음으로 돌아간다(순환). 그래서 작은 풀은 세트마다 되풀이되고, 5의 배수인 풀은 세트마다
 *       겹치지 않는 5개가 나온다. 가져온 문항은 풀의 원래 문항 그대로다 (사본 없음).</li>
 *   <li>풀이 5개 미만이면 발행 거부다 - 순환해도 한 세트 안에 같은 문항이 두 번 들어간다.
 *       M = 5면 어휘는 세트마다 같은 5문항이라 현행과 같다 (하위 호환).</li>
 *   <li>세트 안 출제 순서는 {@link SetLayout}의 문자열 순서다 - 기본값은 음성과 어휘 교차
 *       (v, w, v, w, ...), {@code gn-2026.10.1}은 v, v, w, v, w, w, w다. seq 1..세트 문항 수는
 *       세트를 만들 때 부여한다.</li>
 * </ul>
 * 순환은 09-01 규칙("마지막 세트가 모자라면 풀의 처음부터 채운다")과 같은 결과를 낸다 -
 * N = 34의 세트 7은 양쪽 규칙 모두 poolIndex 31, 32, 33, 34, 1이다. 나머지 연산으로 적으면
 * 두 풀에 같은 식을 쓸 수 있어 규칙이 하나로 준다.
 * <p>
 * 예: N = 34, M = 5면 세트 7개이고 세트 7의 음성은 poolIndex 31, 32, 33, 34, 1, 어휘는 매
 * 세트가 1, 2, 3, 4, 5다. N = M = 145면 세트 29개이고 양쪽 다 순환 없이 딱 나뉜다. 같은 풀을
 * VVWVWWW(v = 3, w = 4)로 나누면 세트 49개다 - 음성은 세트 49가 poolIndex 145, 1, 2이고, 어휘는
 * 세트 37부터 처음으로 돌아가 poolIndex 1..51이 두 세트에 실린다 (2026-10-04, 풀 크기는 유지).
 */
final class VoiceSets {

    private VoiceSets() {
    }

    /**
     * 두 풀에서 유도되는 세트 수 = max(ceil(N / v), ceil(M / w)).
     *
     * @param layout             세트 구성 - v와 w를 준다
     * @param voicePoolSize      음성 풀 크기 N
     * @param vocabularyPoolSize 어휘 풀 크기 M
     * @throws IllegalArgumentException 풀이 세트가 가져오는 수보다 작을 때
     */
    static int setCount(SetLayout layout, int voicePoolSize, int vocabularyPoolSize) {
        requirePoolSize(voicePoolSize, layout.voiceCount(), "음성 문장");
        requirePoolSize(vocabularyPoolSize, layout.vocabularyCount(), "어휘");
        return Math.max(ceilDiv(voicePoolSize, layout.voiceCount()),
                ceilDiv(vocabularyPoolSize, layout.vocabularyCount()));
    }

    /**
     * 세트 k(1부터)가 크기 {@code poolSize}인 풀에서 가져오는 poolIndex(1부터) {@code perSet}개.
     * <p>
     * 자리는 (k-1)*perSet부터 perSet칸이고 풀 크기를 넘으면 풀의 처음으로 돌아간다. 세트 번호가
     * 그 풀 하나만으로 셈한 세트 수를 넘어도 된다 - 세트 수는 세트가 더 많이 필요한 쪽 풀이
     * 정하므로, 다른 풀이 되풀이해 채우는 것이 정상 동작이다.
     *
     * @throws IllegalArgumentException 풀이 perSet개 미만이거나 세트 번호가 1 미만일 때
     */
    static List<Integer> poolIndexes(int poolSize, int perSet, int set) {
        requirePoolSize(poolSize, perSet, "문항");
        if (set < 1) {
            throw new IllegalArgumentException("세트 번호는 1부터다: " + set);
        }
        List<Integer> indexes = new ArrayList<>(perSet);
        // long으로 셈한다 - set이 커도 (set-1)*perSet가 int를 넘어 음수 인덱스가 되지 않는다.
        long first = (long) (set - 1) * perSet;
        for (int offset = 0; offset < perSet; offset++) {
            indexes.add((int) ((first + offset) % poolSize) + 1);
        }
        return List.copyOf(indexes);
    }

    /**
     * 풀 정의에서 세트 정의 전부를 유도한다. 입력은 검증을 통과한 풀 정의(seq 오름차순)여야 한다.
     * 구성은 풀 정의의 {@code setLayout}이다 (없으면 {@link SetLayout#LEGACY}).
     * <p>
     * 세트 정의의 문항은 풀의 원래 문항 그대로이고 seq만 1..세트 문항 수로 새로 매긴다 - 세트를
     * 어느 순서로 응시하든 클라이언트는 같은 구성의 목록을 받는다.
     *
     * @return 세트 번호 순(1..세트 수)의 세트 정의 목록
     */
    static List<TestDefinition> derive(TestDefinition pool) {
        SetLayout layout = SetLayout.of(pool.setLayout());
        List<TestDefinition.Item> voicePool = pool.items().stream()
                .filter(item -> item.type() == TestDefinition.ItemType.VOICE)
                .toList();
        List<TestDefinition.Item> vocabularyPool = pool.items().stream()
                .filter(item -> item.type() == TestDefinition.ItemType.VOCABULARY)
                .toList();
        int setCount = setCount(layout, voicePool.size(), vocabularyPool.size());

        List<TestDefinition> sets = new ArrayList<>(setCount);
        for (int set = 1; set <= setCount; set++) {
            sets.add(new TestDefinition(pool.testVersion(), pool.scoreVersion(), pool.dialect(),
                    pool.estimatedDurationSec(), pool.setLayout(),
                    arrange(layout, pick(voicePool, layout.voiceCount(), set),
                            pick(vocabularyPool, layout.vocabularyCount(), set))));
        }
        return List.copyOf(sets);
    }

    /** 세트 k가 이 풀에서 가져오는 문항 perSet개 - 풀의 원래 문항 그대로다. */
    private static List<TestDefinition.Item> pick(List<TestDefinition.Item> pool, int perSet, int set) {
        return poolIndexes(pool.size(), perSet, set).stream()
                .map(index -> pool.get(index - 1))
                .toList();
    }

    /**
     * 구성의 자리 순서대로 놓고 seq 1..세트 문항 수를 부여한다. 각 유형 안에서는 풀에서 가져온
     * 순서를 지킨다 - {@link SetLayout#LEGACY}면 v, w, v, w, ... 교차가 되어 KAN-182와 같다.
     */
    private static List<TestDefinition.Item> arrange(SetLayout layout, List<TestDefinition.Item> voices,
                                                     List<TestDefinition.Item> vocabulary) {
        Iterator<TestDefinition.Item> nextVoice = voices.iterator();
        Iterator<TestDefinition.Item> nextVocabulary = vocabulary.iterator();
        List<TestDefinition.Item> items = new ArrayList<>(layout.size());
        int seq = 1;
        for (TestDefinition.ItemType slot : layout.slots()) {
            TestDefinition.Item item = slot == TestDefinition.ItemType.VOICE ? nextVoice.next() : nextVocabulary.next();
            items.add(item.withSeq(seq++));
        }
        return List.copyOf(items);
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    /** 풀이 세트가 가져오는 수보다 작으면 순환해도 한 세트 안에 같은 문항이 두 번 들어간다. */
    private static void requirePoolSize(int poolSize, int perSet, String what) {
        if (poolSize < perSet) {
            throw new IllegalArgumentException(
                    what + " 풀은 " + perSet + "개 이상이어야 한다: " + poolSize);
        }
    }
}
