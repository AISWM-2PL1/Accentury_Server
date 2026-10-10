package app.accentury.backend.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * 올리고 내릴 음절 하나 - 높낮이 순서가 경남 화자와 다른 어절 (KAN-267, 명세서 §3.19).
 * <p>
 * 모델 order 항목의 글자를 그대로 옮긴다 (2026-10-10 결정 - 인덱스로 바꾸지 않는다). {@code raise}와 {@code lower}는
 * 응답에 null로도 실린다 - 클라이언트가 "값 없음"을 키 부재와 헷갈리지 않게 한다.
 *
 * @param word  어절 글자
 * @param raise 경남 화자가 가장 높이는 음절 - 없으면 null
 * @param lower 사용자가 가장 높인 음절 - 없으면 null
 * @param split 경남 화자끼리 순서가 갈리는 어절인가 (모델 {@code refAgree} 0.6 미만) - true면 두 값이 null이다.
 *              false인데 두 값이 null이면 가장 높인 음절은 경남 화자와 같고 나머지 순서만 다른 어절이다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PitchFeedback(String word, @Nullable String raise, @Nullable String lower, boolean split) {

    /** 화면에 올리고 내릴 음절로 보여 줄 값이 있는가 - 100점 처리 규칙의 입력이다 (§3.19). */
    boolean hasSyllable() {
        return raise != null || lower != null;
    }
}
