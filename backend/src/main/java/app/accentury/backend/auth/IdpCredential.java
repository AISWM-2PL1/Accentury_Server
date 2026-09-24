package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * 클라이언트가 IdP SDK에서 받아 넘긴 것 (명세서 §3.9 요청). 어느 필드가 필요한지는 provider가 정하고,
 * 필수 필드의 존재는 {@code AuthService}가 먼저 확인했다.
 *
 * @param provider    IdP
 * @param token       구글과 애플은 ID 토큰(JWT), 카카오와 네이버는 SDK access token
 * @param rawNonce    애플만 - 클라이언트가 만든 원문 nonce. 애플 요청에는 이것의 SHA-256을 실었다.
 * @param appleName   애플 최초 로그인만 - 애플이 한 번만 주는 이름 (토큰에는 없다)
 */
public record IdpCredential(Provider provider, String token, @Nullable String rawNonce, @Nullable String appleName) {

    /** 토큰 원문을 찍지 않는다 - record 기본 toString은 모든 필드를 찍는다 (§2.6). */
    @Override
    public String toString() {
        return "IdpCredential[provider=" + provider + "]";
    }
}
