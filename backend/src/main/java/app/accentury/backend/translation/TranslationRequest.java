package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

/** {@code POST /v0/translations}의 본문 (명세서 §3.18). {@code text} 누락과 공백뿐인 값은 400이다. */
record TranslationRequest(@Nullable String text) {
}
