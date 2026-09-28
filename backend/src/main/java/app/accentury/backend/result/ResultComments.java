package app.accentury.backend.result;

import app.accentury.backend.common.AccenturyProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 결과 코멘트의 선택 (KAN-249, API 명세서 §3.7 comment).
 * <p>
 * 코멘트는 등급이 아니라 억양과 단어 점수의 비교로 고른다. 등급은 종합 점수 하나로만 정해져서,
 * 등급별 고정 코멘트는 "단어에서 들켰다"처럼 어느 쪽이 모자란지 단정하다가 실제 점수와 반대가
 * 되는 경우가 있었다 (단어 100, 억양 60이어도 종합 73으로 명예주민). 비교는 응답에 실리는
 * 정수 점수로 해서, 화면에 보이는 두 숫자와 코멘트가 서로 어긋나지 않는다.
 * <p>
 * 문구의 정본은 설정({@code accentury.result.comments})이고, 이 클래스는 기동 시 완결성을
 * 강제한다 - {@link TierAssets}와 같은 이유로, 결과 확정 뒤에 문구가 없다는 것을 알게 되면
 * 저장된 결과를 응답으로 만들 수 없다.
 */
@Component
class ResultComments {

    /** 비교 문구 안에서 한 단계 위 등급의 이름으로 바뀌는 자리 표시 */
    static final String NEXT_TIER = "{nextTier}";

    private final String intonationAhead;
    private final String even;
    private final String vocabularyAhead;
    private final String top;

    ResultComments(AccenturyProperties properties) {
        AccenturyProperties.Comments comments = properties.result().comments();
        this.intonationAhead = requireComparison(comments.intonationAhead(), "intonation-ahead");
        this.even = requireComparison(comments.even(), "even");
        this.vocabularyAhead = requireComparison(comments.vocabularyAhead(), "vocabulary-ahead");
        String topComment = comments.top();
        require(hasText(topComment), "accentury.result.comments.top이 비어 있다");
        this.top = topComment;
    }

    /**
     * 결과 하나의 코멘트.
     *
     * @param nextTierName 한 단계 위 등급의 이름. null이면 최고 등급이라 점수와 무관하게 고정 문구다.
     */
    String commentFor(int intonation, int vocabulary, @Nullable String nextTierName) {
        if (nextTierName == null) {
            return top;
        }
        String template = intonation > vocabulary ? intonationAhead
                : intonation < vocabulary ? vocabularyAhead
                : even;
        return template.replace(NEXT_TIER, nextTierName);
    }

    /** 자리 표시가 빠진 비교 문구는 모든 등급에 같은 문장이 나가는 조용한 실패라 기동 거부다. */
    private static String requireComparison(@Nullable String comment, String key) {
        require(hasText(comment), "accentury.result.comments." + key + "이 비어 있다");
        require(comment.contains(NEXT_TIER),
                "accentury.result.comments." + key + "에 다음 등급 자리 표시 " + NEXT_TIER + "가 없다");
        return comment;
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException("결과 코멘트 설정 거부 - " + message);
        }
    }
}
