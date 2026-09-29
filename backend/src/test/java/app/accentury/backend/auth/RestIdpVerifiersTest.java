package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 카카오와 네이버 사용자 조회 검증 (KAN-223 요구 7, 명세서 §3.9). MockWebServer가 IdP API 자리에 서서 성공, 카카오 app_id
 * 불일치, 4xx, 5xx, 타임아웃을 흉내 낸다.
 */
@ExtendWith(OutputCaptureExtension.class)
class RestIdpVerifiersTest {

    private static final String KAKAO_APP_ID = "1234567";
    private static final String NAVER_CLIENT_ID = "naverClientId01";
    private static final String NAVER_CLIENT_SECRET = "naverClientSecret01";

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
        return auth(kakaoAppId, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET, timeout);
    }

    private AccenturyProperties.Auth auth(String kakaoAppId, @Nullable String naverClientId,
                                          @Nullable String naverClientSecret, Duration timeout) {
        AccenturyProperties.Auth base = PropertiesFixture.auth();
        String url = server.url("").toString().replaceAll("/$", "");
        return new AccenturyProperties.Auth(base.jwtSecret(), base.issuer(), base.accessTokenTtl(), base.refreshTokenTtl(),
                base.rateLimitPerMinute(), false, null, null, kakaoAppId, naverClientId, naverClientSecret,
                base.googleJwksUrl(), base.appleJwksUrl(), url, url, url, timeout, base.privacyPolicyVersion());
    }

    private KakaoIdpVerifier kakao() {
        return new KakaoIdpVerifier(auth(KAKAO_APP_ID, Duration.ofSeconds(2)), JsonMapper.builder().build());
    }

    private NaverIdpVerifier naver(Duration timeout) {
        return new NaverIdpVerifier(auth(KAKAO_APP_ID, timeout), JsonMapper.builder().build());
    }

    /** 네이버 토큰 교환 성공 응답 - 새 access token은 SDK가 준 것과 다른 값이다. */
    private void enqueueNaverExchange() {
        enqueueJson(200, "{\"access_token\": \"exchanged-access\", \"token_type\": \"bearer\", \"expires_in\": \"3600\"}");
    }

    private static IdpCredential naverCredential() {
        return new IdpCredential(Provider.NAVER, "sdk-access", null, null, "sdk-refresh");
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

        IdpProfile profile = kakao().verify(new IdpCredential(Provider.KAKAO, "kakao-token", null, null, null));

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

        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "other-app-token", null, null, null)));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 카카오_음력_생일과_미인증_이메일은_받지_않는다() {
        enqueueJson(200, "{\"id\": 1, \"app_id\": 1234567}");
        enqueueJson(200, """
                {"id": 1, "kakao_account": {"email": "x@kakao.com", "is_email_valid": true, "is_email_verified": false,
                  "birthyear": "1999", "birthday": "0302", "birthday_type": "LUNAR"}}
                """);

        IdpProfile profile = kakao().verify(new IdpCredential(Provider.KAKAO, "t", null, null, null));

        assertNull(profile.email());
        assertNull(profile.birthDate());
    }

    @Test
    void 카카오가_401이면_401이다() {
        enqueueJson(401, "{\"code\": -401, \"msg\": \"InvalidTokenException\"}");

        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "expired", null, null, null)));
    }

    @Test
    void 카카오가_5xx면_502다() {
        enqueueJson(500, "{}");

        assertUnavailable(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "t", null, null, null)));
    }

    @Test
    void IdP_호출_한도_초과_429는_토큰_무효가_아니라_502다() {
        // 401로 내면 앱이 멀쩡한 토큰을 버리고 사용자를 다시 로그인시킨다 (PR #2 리뷰).
        enqueueJson(429, "{}");

        assertUnavailable(() -> kakao().verify(new IdpCredential(Provider.KAKAO, "t", null, null, null)));
    }

    @Test
    void 실제_IdP_경로에서도_토큰_원문이_로그에_남지_않는다(CapturedOutput output) {
        // 가짜 IdP 경로(AuthApiTest)와 달리 IdpHttp가 실제 HTTP 호출을 하고 실패를 로그로 남기는 경로다 (PR #2 리뷰).
        String kakaoToken = "kakao-raw-access-token-9f8e7d6c5b4a";
        String naverToken = "naver-raw-access-token-1a2b3c4d5e6f";
        enqueueJson(401, "{\"code\": -401, \"msg\": \"InvalidTokenException\"}");
        enqueueJson(500, "{}");
        enqueueJson(200, "{\"id\": 1, \"app_id\": 9999999}");

        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, kakaoToken, null, null, null)));
        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(
                new IdpCredential(Provider.NAVER, naverToken, null, null, naverToken)));
        assertInvalid(() -> kakao().verify(new IdpCredential(Provider.KAKAO, kakaoToken, null, null, null)));

        assertFalse(output.getAll().contains(kakaoToken), "카카오 토큰 원문이 로그에 남았다");
        assertFalse(output.getAll().contains(naverToken), "네이버 토큰 원문이 로그에 남았다");
    }

    @Test
    void 카카오_앱_ID가_자리_표시_값이면_IdP를_부르지_않고_401이다() {
        KakaoIdpVerifier unset = new KakaoIdpVerifier(auth("unset-put-parameter-after-apply", Duration.ofSeconds(2)),
                JsonMapper.builder().build());

        assertInvalid(() -> unset.verify(new IdpCredential(Provider.KAKAO, "t", null, null, null)));
        assertEquals(0, server.getRequestCount());
    }

    // === 네이버 ===

    @Test
    void 네이버_정상_토큰은_정보를_주고_휴대폰_번호는_버린다() {
        enqueueNaverExchange();
        enqueueJson(200, """
                {"resultcode": "00", "message": "success", "response": {
                  "id": "naver-id-1", "email": "user@naver.com", "name": "네이버사람", "nickname": "닉",
                  "profile_image": "https://ssl.pstatic.net/p.gif", "gender": "M", "birthyear": "2001",
                  "birthday": "10-01", "mobile": "010-0000-0000"}}
                """);

        IdpProfile profile = naver(Duration.ofSeconds(2)).verify(naverCredential());

        assertEquals("naver-id-1", profile.subject());
        assertEquals("user@naver.com", profile.email());
        assertEquals("네이버사람", profile.name());
        assertEquals(Gender.MALE, profile.gender());
        assertEquals(LocalDate.of(2001, 10, 1), profile.birthDate());
        // 휴대폰 번호를 담을 자리 자체가 없다 - IdpProfile에 필드가 없다.
        assertEquals(8, IdpProfile.class.getRecordComponents().length);
    }

    @Test
    void 네이버_교환_성공_시_사용자_조회에는_교환된_토큰을_쓴다() throws Exception {
        // 다른 앱의 토큰을 막는 검사의 핵심 (KAN-243) - SDK access token이 아니라 우리 Client로 교환한 토큰이 조회에 가야
        // 교환 성공이 곧 "우리 앱의 토큰"이라는 증명이 된다.
        enqueueNaverExchange();
        enqueueJson(200, "{\"resultcode\": \"00\", \"response\": {\"id\": \"naver-id-1\"}}");

        naver(Duration.ofSeconds(2)).verify(naverCredential());

        RecordedRequest exchange = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", exchange.getMethod());
        assertEquals("/oauth2.0/token", exchange.getUrl().encodedPath());
        // 시크릿은 쿼리 문자열이 아니라 본문에 싣는다.
        assertNull(exchange.getUrl().encodedQuery());
        String form = exchange.getBody().utf8();
        assertTrue(form.contains("grant_type=refresh_token"), form);
        assertTrue(form.contains("client_id=" + NAVER_CLIENT_ID), form);
        assertTrue(form.contains("client_secret=" + NAVER_CLIENT_SECRET), form);
        assertTrue(form.contains("refresh_token=sdk-refresh"), form);
        RecordedRequest me = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/v1/nid/me", me.getUrl().encodedPath());
        assertEquals("Bearer exchanged-access", me.getHeaders().get("Authorization"));
    }

    @Test
    void 네이버_교환_실패는_401이고_사용자_조회를_부르지_않는다() {
        // 다른 앱이 발급받은 refresh token - 네이버는 오류를 200 + error로도 준다.
        enqueueJson(200, "{\"error\": \"invalid_request\", \"error_description\": \"no valid data in session\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 네이버_교환이_200에_server_error를_주면_502다() {
        // 토큰이 아니라 네이버 장애다 - 401이면 앱이 사용자를 다시 로그인시킨다 (Codex 리뷰 P2).
        enqueueJson(200, "{\"error\": \"server_error\", \"error_description\": \"internal\"}");

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 네이버_교환이_4xx면_401이다() {
        enqueueJson(401, "{\"error\": \"invalid_client\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
    }

    @Test
    void 네이버_교환이_5xx면_502다() {
        enqueueJson(503, "{}");

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
    }

    @Test
    void 네이버_Client_ID나_Secret이_미설정이면_IdP를_부르지_않고_401이고_WARN을_남긴다(CapturedOutput output) {
        NaverIdpVerifier noId = new NaverIdpVerifier(auth(KAKAO_APP_ID, "unset-put-parameter-after-apply",
                NAVER_CLIENT_SECRET, Duration.ofSeconds(2)), JsonMapper.builder().build());
        NaverIdpVerifier noSecret = new NaverIdpVerifier(auth(KAKAO_APP_ID, NAVER_CLIENT_ID, null,
                Duration.ofSeconds(2)), JsonMapper.builder().build());

        assertInvalid(() -> noId.verify(naverCredential()));
        assertInvalid(() -> noSecret.verify(naverCredential()));
        assertEquals(0, server.getRequestCount());
        assertTrue(output.getAll().contains("WARN") && output.getAll().contains("네이버 로그인 미설정"), output.getAll());
    }

    @Test
    void 네이버_시크릿과_refresh_token이_로그에_남지_않는다(CapturedOutput output) {
        enqueueJson(200, "{\"error\": \"invalid_request\", \"error_description\": \"sdk-refresh\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));

        assertFalse(output.getAll().contains(NAVER_CLIENT_SECRET), "Client Secret이 로그에 남았다");
        assertFalse(output.getAll().contains("sdk-refresh"), "refresh token이 로그에 남았다");
    }

    @Test
    void 네이버_resultcode가_00이_아니면_401이다() {
        enqueueNaverExchange();
        enqueueJson(200, "{\"resultcode\": \"024\", \"message\": \"Authentication failed\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
    }

    @Test
    void 네이버가_401이면_401이다() {
        enqueueNaverExchange();
        enqueueJson(401, "{\"errorCode\": \"024\", \"errorMessage\": \"Authentication failed\"}");

        assertInvalid(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
    }

    @Test
    void 네이버가_5xx면_502다() {
        enqueueNaverExchange();
        enqueueJson(503, "{}");

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
    }

    @Test
    void 네이버가_응답하지_않으면_502다() {
        server.enqueue(new MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build());

        assertUnavailable(() -> naver(Duration.ofMillis(300)).verify(naverCredential()));
    }

    @Test
    void 응답이_JSON이_아니면_502다() {
        server.enqueue(new MockResponse.Builder().code(200).body("<html>maintenance</html>").build());

        assertUnavailable(() -> naver(Duration.ofSeconds(2)).verify(naverCredential()));
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
