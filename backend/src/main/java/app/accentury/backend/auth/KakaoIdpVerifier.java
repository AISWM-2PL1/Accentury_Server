package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 카카오 SDK access token (명세서 §3.9).
 * <p>
 * <b>먼저 토큰이 우리 앱의 것인지 본다.</b> {@code GET /v1/user/access_token_info}의 {@code app_id}가 우리 카카오 앱 ID와
 * 같아야 한다. 이 검사가 없으면 다른 카카오 앱이 받은 사용자 토큰으로 우리 계정에 로그인할 수 있다 - 사용자 조회
 * API는 어느 앱의 토큰이든 답하기 때문이다. 그다음 {@code GET /v2/user/me}로 정보를 읽는다.
 * <p>
 * 받는 정보: 이메일은 유효({@code is_email_valid})하고 인증({@code is_email_verified})된 것만, 생년월일은
 * 출생연도와 생일(MMDD)이 둘 다 있고 양력일 때만이다 - 음력 생일을 양력 날짜로 저장하면 틀린 값이 남는다.
 * 동의하지 않은 항목은 응답에 없고, 그 항목은 추가 정보 화면이 채운다.
 */
final class KakaoIdpVerifier implements IdpVerifier {

    private static final Logger log = LoggerFactory.getLogger(KakaoIdpVerifier.class);

    private final IdpHttp http;
    private final @Nullable String appId;

    KakaoIdpVerifier(AccenturyProperties.Auth auth, ObjectMapper objectMapper) {
        this.http = new IdpHttp("카카오", auth.kakaoApiBaseUrl(), auth.idpTimeout(), objectMapper);
        this.appId = JwksIdTokens.configured(auth.kakaoAppId()) ? auth.kakaoAppId() : null;
        if (appId == null) {
            log.warn("카카오 로그인 미설정 - 앱 ID가 없어 모든 토큰을 거절한다");
        }
    }

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    public IdpProfile verify(IdpCredential credential) {
        if (appId == null) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        JsonNode info = http.get("/v1/user/access_token_info", credential.token());
        if (!appId.equals(text(info.get("app_id")))) {
            // 다른 앱의 토큰이다. 받은 app_id는 공개 값이지만 남길 이유가 없다.
            log.info("카카오 토큰의 app_id가 우리 앱과 다르다");
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        JsonNode me = http.get("/v2/user/me", credential.token());
        String id = text(me.get("id"));
        if (id == null || !id.equals(text(info.get("id")))) {
            // 두 호출 사이에 토큰 주인이 바뀔 수는 없다 - 이상한 응답은 장애로 본다.
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        }
        JsonNode account = me.path("kakao_account");
        JsonNode profile = account.path("profile");
        boolean emailUsable = account.path("is_email_valid").asBoolean(false)
                && account.path("is_email_verified").asBoolean(false);
        return new IdpProfile(Provider.KAKAO, id,
                emailUsable ? text(account.get("email")) : null,
                text(account.get("name")),
                birthDate(account),
                gender(text(account.get("gender"))),
                text(profile.get("nickname")),
                text(profile.get("profile_image_url")));
    }

    private static @Nullable LocalDate birthDate(JsonNode account) {
        String year = text(account.get("birthyear"));
        String monthDay = text(account.get("birthday"));
        String type = text(account.get("birthday_type"));
        if (year == null || monthDay == null || monthDay.length() != 4 || "LUNAR".equals(type)) {
            return null;
        }
        return date(year, monthDay.substring(0, 2), monthDay.substring(2));
    }

    static @Nullable LocalDate date(String year, String month, String day) {
        try {
            LocalDate date = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return date.isBefore(LocalDate.now()) ? date : null;
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private static @Nullable Gender gender(@Nullable String value) {
        if ("male".equals(value)) {
            return Gender.MALE;
        }
        if ("female".equals(value)) {
            return Gender.FEMALE;
        }
        return null;
    }

    /** 문자열이든 숫자든 텍스트로 - 카카오의 id와 app_id는 숫자다. 없거나 null이면 null. */
    static @Nullable String text(@Nullable JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainer()) {
            return null;
        }
        String value = node.asString();
        return value.isBlank() ? null : value;
    }
}
