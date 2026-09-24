package app.accentury.backend.auth;

/**
 * IdP 하나의 토큰 검증 (명세서 §3.9 IdP별 검증 표). 구현은 IdP마다 하나이고, 테스트는 가짜 구현으로 바꿔
 * IdP를 부르지 않고 로그인 흐름을 검증한다 (KAN-223 요구 2).
 * <p>
 * 실패는 두 갈래뿐이다. 토큰이 틀렸으면 401 {@code AUTH_IDP_TOKEN_INVALID}, IdP에 닿지 못했거나 IdP가 5xx면
 * 502 {@code AUTH_IDP_UNAVAILABLE}이다. 둘을 섞으면 클라이언트가 재시도할지 다시 로그인할지를 모른다.
 */
public interface IdpVerifier {

    Provider provider();

    /**
     * @throws app.accentury.backend.common.ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는
     *                                                    502 {@code AUTH_IDP_UNAVAILABLE}
     */
    IdpProfile verify(IdpCredential credential);
}
