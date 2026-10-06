package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 응답의 {@code voiceConsent} (KAN-269, 명세서 §3.11, §3.15) - 앱이 동의 화면과 설정의 토글을 그리는 데 쓴다.
 *
 * @param consented      지금 음성 저장 동의가 유효한가
 * @param version        동의한 버전 - 동의가 유효할 때만 있다
 * @param consentedAt    동의 시각 - 동의가 유효할 때만 있다
 * @param currentVersion 게시 중인 동의 버전 - 동의 요청에 이 값을 싣는다
 */
record VoiceConsentView(boolean consented, @Nullable String version, @Nullable Instant consentedAt,
                        String currentVersion) {

    static VoiceConsentView of(AppUser user, String currentVersion) {
        boolean consented = user.hasVoiceConsent();
        return new VoiceConsentView(consented, consented ? user.voiceConsentVersion() : null,
                consented ? user.voiceConsentAt() : null, currentVersion);
    }
}
