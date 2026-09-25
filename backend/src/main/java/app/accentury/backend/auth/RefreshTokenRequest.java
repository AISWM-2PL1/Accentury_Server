package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/auth/refresh}(§3.12)와 {@code POST /v0/auth/logout}(§3.13)의 요청 본문.
 *
 * @param refreshToken Refresh 원문 ({@code rt_...})
 */
record RefreshTokenRequest(@Nullable String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[]";
    }
}
