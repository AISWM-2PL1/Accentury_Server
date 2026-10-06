package app.accentury.backend.testdefinition;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 세트 하나의 문항 구성과 출제 순서 (KAN-260, 2026-10-04 확정).
 * <p>
 * 발행본의 optional 필드 {@code setLayout}이 정본이다. V는 음성, W는 어휘 문항 한 자리이고
 * 문자열 순서가 곧 출제 순서다 - {@code "VVWVWWW"}면 세트가 음성 풀에서 3개, 어휘 풀에서 4개를
 * 가져와 음성, 음성, 어휘, 음성, 어휘, 어휘, 어휘로 놓는다.
 * <p>
 * <b>규칙을 코드가 아니라 발행본에 두는 이유</b>: 세트는 기동할 때마다 풀에서 다시 유도된다
 * ({@link VoiceSets}). 규칙이 코드 상수면 그 상수를 바꾸는 배포가 이미 발행된 정의의 세트까지
 * 바꾸고, 그 정의로 만든 세션의 제출 검증과 재집계가 함께 어긋난다. 발행본에 두면 규칙도 정의와
 * 함께 발행 후 불변이다 (KAN-26).
 * <p>
 * 필드가 없는 발행본은 {@link #LEGACY}로 읽는다. KAN-182 규칙(각 풀에서 5개, 음성과 어휘 교차)을
 * 그대로 문자열로 적은 것이라, {@code gn-2026.09.4}까지의 세트와 응답 본문, ETag가 바이트 단위로
 * 같다.
 *
 * @param pattern V와 W로만 된 문자열. 둘 다 하나 이상 있어야 한다 - 한쪽이 없으면 집계의 억양
 *                또는 단어 점수가 0으로 나누기가 된다 ({@code ScoreAggregator}).
 */
record SetLayout(String pattern) {

    /** {@code setLayout}이 없는 발행본의 구성 - 음성 5 + 어휘 5 교차 (문항 구성 확정 2026-07-27). */
    static final SetLayout LEGACY = new SetLayout("VWVWVWVWVW");

    SetLayout {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("setLayout이 비어 있다");
        }
        for (int i = 0; i < pattern.length(); i++) {
            char slot = pattern.charAt(i);
            if (slot != 'V' && slot != 'W') {
                throw new IllegalArgumentException(
                        "setLayout은 V(음성)와 W(어휘)만 쓸 수 있다: " + pattern);
            }
        }
        if (pattern.indexOf('V') < 0 || pattern.indexOf('W') < 0) {
            throw new IllegalArgumentException(
                    "setLayout에는 V와 W가 각각 하나 이상 있어야 한다: " + pattern);
        }
    }

    /** 발행본의 필드 값으로 구성을 정한다. 필드가 없으면 {@link #LEGACY}다. */
    static SetLayout of(@Nullable String pattern) {
        return pattern == null ? LEGACY : new SetLayout(pattern);
    }

    /** 세트 하나가 음성 풀에서 가져오는 수 v */
    int voiceCount() {
        return count('V');
    }

    /** 세트 하나가 어휘 풀에서 가져오는 수 w */
    int vocabularyCount() {
        return count('W');
    }

    /** 세트 하나의 문항 수 = v + w */
    int size() {
        return pattern.length();
    }

    /** 출제 순서대로의 자리별 문항 유형 */
    List<TestDefinition.ItemType> slots() {
        return pattern.chars()
                .mapToObj(slot -> slot == 'V' ? TestDefinition.ItemType.VOICE : TestDefinition.ItemType.VOCABULARY)
                .toList();
    }

    private int count(char type) {
        return (int) pattern.chars().filter(slot -> slot == type).count();
    }
}
