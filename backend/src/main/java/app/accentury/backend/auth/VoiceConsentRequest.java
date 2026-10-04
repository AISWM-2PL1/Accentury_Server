package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code PUT /v0/users/me/voice-consent}의 본문 (KAN-269, 명세서 §3.15).
 *
 * @param version 사용자가 본 음성 저장 동의 문구의 버전 - 서버 게시 버전과 정확히 같아야 한다.
 */
record VoiceConsentRequest(@Nullable String version) {
}
