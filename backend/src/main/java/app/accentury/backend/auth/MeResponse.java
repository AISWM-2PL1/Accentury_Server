package app.accentury.backend.auth;

/**
 * {@code GET /v0/users/me}(§3.11)와 {@code PUT /v0/users/me/profile}(§3.10)의 200 응답.
 */
record MeResponse(ProfileStatus profileStatus, UserView user) {

    static MeResponse of(AppUser user) {
        return new MeResponse(ProfileStatus.of(user), UserView.of(user));
    }
}
