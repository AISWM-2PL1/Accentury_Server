package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/translations}의 본문 (명세서 §3.18). {@code text} 누락과 공백뿐인 값은 400이다.
 *
 * @param direction 번역 방향 {@code toDialect} | {@code toStandard} (KAN-316). 없으면 {@code toDialect}라 KAN-316 전
 *                  클라이언트는 그대로 돈다. 그 밖의 값은 400 {@code VALIDATION_FAILED}
 */
record TranslationRequest(@Nullable String text, @Nullable String direction) {
}
