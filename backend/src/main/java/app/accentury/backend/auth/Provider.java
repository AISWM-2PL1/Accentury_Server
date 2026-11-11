package app.accentury.backend.auth;

import java.util.List;

/**
 * 계정의 출처 (명세서 §3.9, §3.21). 소셜 로그인 IdP 넷과 게스트다. 애플은 iOS에서만 쓴다 (KAN-224) - 서버는
 * 플랫폼을 가리지 않는다.
 * <p>
 * 저장 값은 이름 그대로다 ({@code app_user.provider}, V18의 check 제약과 같은 목록).
 * <p>
 * {@link #GUEST}는 IdP가 아니다 (KAN-329) - 로그인({@code POST /v0/auth/login})의 {@code provider}로 받지 않고
 * 검증기도 없다. {@code POST /v0/auth/guest}만 이 값의 계정을 만든다.
 */
public enum Provider {
    GOOGLE, KAKAO, NAVER, APPLE, GUEST;

    /** 소셜 로그인 IdP 넷 - 로그인이 받는 값이고 검증기가 있어야 하는 값이다. */
    static final List<Provider> SOCIAL = List.of(GOOGLE, KAKAO, NAVER, APPLE);

    /** ID 토큰(JWT)을 보내는 IdP인가 - 아니면 SDK access token을 보낸다. */
    boolean usesIdToken() {
        return this == GOOGLE || this == APPLE;
    }
}
