package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 카카오와 네이버 사용자 조회 검증 (KAN-223 요구 7, 명세서 §3.9). MockWebServer가 IdP API 자리에 서서 성공, 카카오 app_id
 * 불일치, 4xx, 5xx, 타임아웃을 흉내 낸다.
 */
class RestIdpVerifiersTest {

    private static final String KAKAO_APP_ID = "1234567";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private AccenturyProperties.Auth auth(String kakaoAppId, Duration timeout) {
        AccenturyProperties.Auth base = PropertiesFixture.auth();
        String url = server.url("").toString().replaceAll("/$", "");
        return new AccenturyProperties.Auth(base.jwtSecret(), base.issuer(), base.accessTokenTtl(), base.refreshTokenTtl(),
                base.rateLimitPerMinute(), false, null, null, kakaoAppId,
                base.googleJwksUrl(), base.appleJwksUrl(), url, url, timeout);
    }

    private KakaoIdpVerifier kakao() {
        return new KakaoIdpVerifier(auth(KAKAO_APP_ID, Duration.ofSeconds(2)), JsonMapper.builder().build());
    }

    private NaverIdpVerifier naver(Duration timeout) {
        return new NaverIdpVerifier(auth(KAKAO_APP_ID, timeout), JsonMapper.builder().build());
    }

    private void enqueueJson(int code, String body) {
        server.enqueue(new MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build());
    }

    // === 카카오 ===

    @Test
    void 카카오_정상_토큰은_우리_앱인지_확인한_뒤_동의한_정보를_준다() throws Exception {
        enqueueJson(200, "{\"id\": 4242, \"expires_in\": 7199, \"app_id\": 1234567}");
        enqueueJson(200, """
                {"id": 4242, "kakao_account": {
                  "email": "user@kakao.com", "is_email_valid": true, "is_email_verified": true,
                  "name": "카카오사람", "birthyear": "1999", "birthday": "0302", "birthday_type": "SOLAR",
                  "gender": "female",
                  "profile": {"nickname": "사투리왕", "profile_image_url": "https://k.kakaocdn.net/p.jpg"}}}
                """);

        IdpProfile profile = kakao().verify(new IdpCredential(Provider.KAKAO, "kakao-token", null, null));

        assertEquals("4242", profile.subject());
        assertEquals("user@kakao.com", profile.email());
        assertEquals("카카오사람", profile.name());
        assertEquals(LocalDate.of(1999, 3, 2), profile.birthDate());
        assertEquals(Gender.FEMALE, profile.gender());
        assertEquals("사투리왕", profile.nickname());
        assertEquals("https://k.kakaocdn.net/p.jpg", profile.profileImageUrl());

        RecordedRequest tokenInfo = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/v1/user/access_token_info", tokenInfo.getUrl().encodedPath());
        assertEquals("Bearer kakao-token", tokenInfo.getHeaders().get("Authorization"));
        assertEquals("/v2/user/me", server.takeRequest(1, TimeUnit.SECONDS).getUrl().encodedPath());
    }

    @Test
    void 카카오_다른_앱의_토큰은_401이고_사용자_정보를_묻지_않는다() {
        enqueueJson(200, "{\"id\": 4242, \"expires_in\": 7199, \"app_id\": 9999999}");

        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "other-app-token", null, null)));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 카카오_음력_생일과_미인증_이메일은_받지_않는다() {
        enqueueJson(200, "{\"id\": 1, \"app_id\": 1234567}");
        enqueueJson(200, """
                {"id": 1, "kakao_account": {"email": "x@kakao.com", "is_email_valid": true, "is_email_verified": false,
                  "birthyear": "1999", "birthday": "0302", "birthday_type": "LUNAR"}}
                """);

        IdpProfile profile = kakao().verify(new IdpCredential(Provider.KAKAO, "t", null, null));

        assertNull(profile.email());
        assertNull(profile.birthDate());
    }

    @Test
    void 카카오가_401이면_401이다() {
        enqueueJson(401, "{\"code\": -401, \"msg\": \"InvalidTokenException\"}");

        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "expired", null, null)));
    }

    @Test
    void 카카오가_5xx면_502다() {
        enqueueJson(500, "{}");

        assertUnavailable(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "t", null, null)));
    }

    @Test
    void 카카오_앱_ID가_자리_표시_값이면_IdP를_부르지_않고_401이다() {
        KakaoIdpVerifier unset = new KakaoIdpVerifier(auth("unset-put-parameter-after-apply", Duration.ofSeconds(2)),
                JsonMapper.builder().build());

        assertInvalid(() -> unset.verify(new IdpCredential(Provider.KAKAO, "t", null, null)));
        assertEquals(0, server.getRequestCount());
    }

    // === 네이버 ===

    @Test
    void 네이버_정상_토큰은_정보를_주고_휴대폰_번호는_버린다() {
        enqueueJson(200, """
                {"resultcode": "00", "message": "success", "response": {
                  "id": "naver-id-1", "email": "user@naver.com", "name": "네이버사람", "nickname": "닉",
                  "profile_image": "https://ssl.pstatic.net/p.gif", "gender": "M", "birthyear": "2001",
                  "birthday": "10-01", "mobile": "010-0000-0000"}}
                """);

        IdpProfile profile = naver(Duration.ofSeconds(2)).verify(new IdpCredential(Provider.NAVER, "naver-token", null, null));

        assertEquals("naver-id-1", profile.subject());
        assertEquals("user@naver.com", profile.email());
        assertEquals("네이버사람", profile.name());
        assertEquals(Gender.MALE, profile.gender());
        assertEquals(LocalDate.of(2001, 10, 1), profile.birthDate());
        // 휴대폰 번호를 담을 자리 자체가 없다 - IdpProfile에 필드가 없다.
        assertEquals(8, IdpProfile.class.getRecordComponents().length);
    }

    @Test
    void 네이버_resultcode가_00이_아니면_401이다() {
        enqueueJson(200, "{\"resultcode\": \"024\", \"message\": \"Authentication failed\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(new IdpCredential(Provider.NAVER, "t", null, null)));
    }

    @Test
    void 네이버가_401이면_401이다() {
        enqueueJson(401, "{\"errorCode\": \"024\", \"errorMessage\": \"Authentication failed\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(new IdpCredential(Provider.NAVER, "t", null, null)));
    }

    @Test
    void 네이버가_5xx면_502다() {
        enqueueJson(503, "{}");

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(new IdpCredential(Provider.NAVER, "t", null, null)));
    }

    @Test
    void 네이버가_응답하지_않으면_502다() {
        server.enqueue(new MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build());

        assertUnavailable(() -> naver(Duration.ofMillis(300)).verify(new IdpCredential(Provider.NAVER, "t", null, null)));
    }

    @Test
    void 응답이_JSON이_아니면_502다() {
        server.enqueue(new MockResponse.Builder().code(200).body("<html>maintenance</html>").build());

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(new IdpCredential(Provider.NAVER, "t", null, null)));
    }

    private static void assertInvalid(Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(ErrorCode.AUTH_IDP_TOKEN_INVALID, e.code());
    }

    private static void assertUnavailable(Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(ErrorCode.AUTH_IDP_UNAVAILABLE, e.code());
    }
}
