package app.accentury.backend.auth;

/**
 * {@code GET /v0/users/me}(§3.11), {@code PUT /v0/users/me/profile}(§3.10), 음성 저장 동의 등록과 철회(§3.15)의
 * 200 응답.
 */
record MeResponse(ProfileStatus profileStatus, UserView user, VoiceConsentView voiceConsent) {

    /**
     * @param voiceConsentVersion 게시 중인 음성 저장 동의 버전 (KAN-269) - 응답의 {@code voiceConsent.currentVersion}이다.
     */
    static MeResponse of(AppUser user, String voiceConsentVersion) {
        return new MeResponse(ProfileStatus.of(user), UserView.of(user),
                VoiceConsentView.of(user, voiceConsentVersion));
    }
}
