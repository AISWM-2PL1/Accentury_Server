package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

/**
 * 네이버 SDK access token (명세서 §3.9).
 * <p>
 * {@code GET /v1/nid/me} 하나로 토큰 확인과 정보 조회를 함께 한다 - {@code resultcode}가 {@code 00}이어야 한다.
 * 네이버는 카카오의 {@code app_id}처럼 토큰의 발급 앱을 알려 주는 조회 API가 없어 서버가 보관할 앱 값이 없다
 * (KAN-223 요구 6).
 * <p>
 * 휴대폰 번호({@code mobile})는 응답에 와도 읽지 않는다 - 전화번호는 수집하지 않기로 했다 (KAN-222 확정).
 * 생년월일은 출생연도와 생일(MM-DD)이 둘 다 있을 때만 받는다.
 */
final class NaverIdpVerifier implements IdpVerifier {

    private static final Logger log = LoggerFactory.getLogger(NaverIdpVerifier.class);

    private final IdpHttp http;

    NaverIdpVerifier(AccenturyProperties.Auth auth, ObjectMapper objectMapper) {
        this.http = new IdpHttp("네이버", auth.naverApiBaseUrl(), auth.idpTimeout(), objectMapper);
    }

    @Override
    public Provider provider() {
        return Provider.NAVER;
    }

    @Override
    public IdpProfile verify(IdpCredential credential) {
        JsonNode me = http.get("/v1/nid/me", credential.token());
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
