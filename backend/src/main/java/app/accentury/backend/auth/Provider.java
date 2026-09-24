package app.accentury.backend.auth;

/**
 * 소셜 로그인 IdP (명세서 §3.9). 애플은 iOS에서만 쓴다 (KAN-224) - 서버는 플랫폼을 가리지 않는다.
 * <p>
 * 저장 값은 이름 그대로다 ({@code app_user.provider}, V2의 check 제약과 같은 목록).
 */
public enum Provider {
    GOOGLE, KAKAO, NAVER, APPLE;

    /** ID 토큰(JWT)을 보내는 IdP인가 - 아니면 SDK access token을 보낸다. */
    boolean usesIdToken() {
        return this == GOOGLE || this == APPLE;
    }
}
