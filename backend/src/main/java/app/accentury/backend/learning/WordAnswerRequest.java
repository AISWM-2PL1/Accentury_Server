package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST .../items/{itemId}/answer}의 본문 (명세서 §3.16).
 * <p>
 * 보기를 고른 답안은 {@code choiceId}만 싣는다 - 누락은 400이다. 시간 초과 답안(KAN-360)은 {@code choiceId} 없이
 * {@code timedOut}을 true로 싣는다 - 둘 다 있으면 400이다.
 *
 * @param choiceId 고른 보기 - 시간 초과면 없거나 null
 * @param timedOut 제한 시간 안에 보기를 고르지 않았는지 - 없거나 null이면 false다
 */
record WordAnswerRequest(@Nullable String choiceId, @Nullable Boolean timedOut) {
}
