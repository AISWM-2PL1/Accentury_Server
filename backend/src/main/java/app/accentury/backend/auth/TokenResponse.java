package app.accentury.backend.auth;

/**
 * {@code POST /v0/auth/refresh} 200 응답 (명세서 §3.12) - 새 Access와 회전된 새 Refresh.
 */
record TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresInSec) {

    @Override
    public String toString() {
        return "TokenResponse[]";
    }
}
