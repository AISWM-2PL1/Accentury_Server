package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Set;

/**
 * 네이버 SDK 토큰 (명세서 §3.9).
 * <p>
 * <b>먼저 토큰이 우리 앱의 것인지 본다 (KAN-243).</b> 네이버 사용자 조회 API는 어느 앱의 토큰이든 답하고, 카카오의
 * {@code app_id}처럼 발급 앱을 알려 주는 조회 API도 없다. 그래서 SDK가 준 refresh token을 우리 Client ID와 Secret으로
 * {@code POST /oauth2.0/token}({@code grant_type=refresh_token})에 교환해 본다. refresh token은 발급받은 클라이언트만
 * 교환할 수 있으므로(OAuth 2.0 §6) 성공하면 우리 앱의 토큰이다. 그다음 새로 받은 access token으로
 * {@code GET /v1/nid/me}를 부르고 {@code resultcode}가 {@code 00}이어야 한다. SDK access token은 가짜 IdP 판정에만
 * 쓰고 네이버에 보내지 않는다. 교환으로 받은 토큰은 이 요청 안에서만 쓰고 버린다.
 * <p>
 * 네이버는 교환 오류를 200 + {@code {"error": ...}}로 주기도 한다 - {@code access_token}이 없으면 401이고,
 * 오류 코드가 네이버 쪽 장애({@code server_error}, {@code temporarily_unavailable})면 502다.
 * <p>
 * 휴대폰 번호({@code mobile})는 응답에 와도 읽지 않는다 - 전화번호는 수집하지 않기로 했다 (KAN-222 확정).
 * 생년월일은 출생연도와 생일(MM-DD)이 둘 다 있을 때만 받는다.
 */
final class NaverIdpVerifier implements IdpVerifier {

    private static final Logger log = LoggerFactory.getLogger(NaverIdpVerifier.class);

    /** 토큰이 아니라 네이버 쪽 문제인 OAuth 오류 코드 (RFC 6749 §5.2 밖의 확장 코드, 네이버 문서의 {@code server_error} 포함). */
    private static final Set<String> UPSTREAM_ERRORS = Set.of("server_error", "temporarily_unavailable");

    private final IdpHttp api;
    private final IdpHttp auth;
    private final @Nullable String clientId;
    private final @Nullable String clientSecret;

    NaverIdpVerifier(AccenturyProperties.Auth auth, ObjectMapper objectMapper) {
        this.api = new IdpHttp("네이버", auth.naverApiBaseUrl(), auth.idpTimeout(), objectMapper);
        this.auth = new IdpHttp("네이버", auth.naverAuthBaseUrl(), auth.idpTimeout(), objectMapper);
        boolean configured = JwksIdTokens.configured(auth.naverClientId())
                && JwksIdTokens.configured(auth.naverClientSecret());
        this.clientId = configured ? auth.naverClientId() : null;
        this.clientSecret = configured ? auth.naverClientSecret() : null;
        if (!configured) {
            log.warn("네이버 로그인 미설정 - Client ID나 Secret이 없어 모든 토큰을 거절한다");
        }
    }

    @Override
    public Provider provider() {
        return Provider.NAVER;
    }

    @Override
    public IdpProfile verify(IdpCredential credential) {
        if (clientId == null || clientSecret == null || credential.idpRefreshToken() == null) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        JsonNode me = api.get("/v1/nid/me", exchange(clientId, clientSecret, credential.idpRefreshToken()));
        if (!"00".equals(KakaoIdpVerifier.text(me.get("resultcode")))) {
            log.info("네이버 프로필 조회 실패 resultcode={}", KakaoIdpVerifier.text(me.get("resultcode")));
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        JsonNode response = me.path("response");
        String id = KakaoIdpVerifier.text(response.get("id"));
        if (id == null) {
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        }
        return new IdpProfile(Provider.NAVER, id,
                KakaoIdpVerifier.text(response.get("email")),
                KakaoIdpVerifier.text(response.get("name")),
                birthDate(KakaoIdpVerifier.text(response.get("birthyear")),
                        KakaoIdpVerifier.text(response.get("birthday"))),
                gender(KakaoIdpVerifier.text(response.get("gender"))),
                KakaoIdpVerifier.text(response.get("nickname")),
                KakaoIdpVerifier.text(response.get("profile_image")));
    }

    /** refresh token을 우리 클라이언트로 교환해 새 access token을 받는다. 교환 실패는 다른 앱의 토큰이거나 만료다. */
    private String exchange(String clientId, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        JsonNode issued = auth.postForm("/oauth2.0/token", form);
        String accessToken = KakaoIdpVerifier.text(issued.get("access_token"));
        if (accessToken == null) {
            // 오류 코드만 남긴다 - error_description은 네이버가 정하는 자유 문구다.
            String error = KakaoIdpVerifier.text(issued.get("error"));
            if (UPSTREAM_ERRORS.contains(error)) {
                // 200 본문에 실린 네이버 쪽 장애다 - 401로 내면 앱이 멀쩡한 로그인을 버리고 처음부터 다시 시킨다 (Codex 리뷰 P2).
                log.warn("네이버 토큰 교환 장애 error={}", error);
                throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
            }
            log.info("네이버 토큰 교환 거절 error={}", error);
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        return accessToken;
    }

    private static @Nullable LocalDate birthDate(@Nullable String year, @Nullable String monthDay) {
        if (year == null || monthDay == null || monthDay.length() != 5 || monthDay.charAt(2) != '-') {
            return null;
        }
        return KakaoIdpVerifier.date(year, monthDay.substring(0, 2), monthDay.substring(3));
    }

    /** 네이버는 M, F, U(미상)다. U는 모르는 것과 같다. */
    private static @Nullable Gender gender(@Nullable String value) {
        if ("M".equals(value)) {
            return Gender.MALE;
        }
        if ("F".equals(value)) {
            return Gender.FEMALE;
        }
        return null;
    }
}
