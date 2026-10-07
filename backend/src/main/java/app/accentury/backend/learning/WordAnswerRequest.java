package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

/** {@code POST .../items/{itemId}/answer}의 본문 (명세서 §3.16). {@code choiceId} 누락은 400이다. */
record WordAnswerRequest(@Nullable String choiceId) {
}
