package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/auth/login} 요청 (명세서 §3.9).
 * <p>
 * 필드 검증은 {@link AuthService}가 맡아 공통 오류 봉투로 응답한다 - 그래서 전부 {@code @Nullable}이고 enum도
 * 문자열로 받는다 ({@code FeedbackRequest}와 같은 이유: 모르는 값이 역직렬화 단계에서 끊기면 메시지를 우리가
 * 정하지 못한다).
 *
 * @param provider             {@code GOOGLE | KAKAO | NAVER | APPLE}
 * @param idToken              구글과 애플 - IdP가 준 ID 토큰(JWT)
 * @param accessToken          카카오와 네이버 - SDK가 준 access token
 * @param nonce                애플만 - 원문 nonce (애플 요청에는 SHA-256을 실었다)
 * @param user                 애플 최초 로그인만 - 애플이 한 번만 주는 이름
 * @param privacyConsent       가입이면 true 필수
 * @param privacyPolicyVersion 동의한 개인정보처리방침 버전 - 가입이면 필수
 */
record LoginRequest(@Nullable String provider,
                    @Nullable String idToken,
                    @Nullable String accessToken,
                    @Nullable String nonce,
                    @Nullable User user,
                    @Nullable Boolean privacyConsent,
                    @Nullable String privacyPolicyVersion) {

    /** @param name 애플이 최초 로그인에만 주는 이름 */
    record User(@Nullable String name) {
    }

    /** 토큰과 이름을 찍지 않는다 - record 기본 toString은 모든 필드를 찍는다 (§2.6). */
    @Override
    public String toString() {
        return "LoginRequest[provider=" + provider + "]";
    }
}
