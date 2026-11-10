package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 탈퇴한 카카오, 네이버 계정의 연결 해제 (KAN-343). MockWebServer가 카카오 API와 네이버 인증, API 서버 자리에 선다.
 * <p>
 * 요청 모양은 두 제공자의 문서대로다: 카카오는 어드민 키 방식의 {@code POST /v1/user/unlink}, 네이버는 refresh token
 * 교환 뒤 {@code grant_type=delete}다. 이 클래스가 예외를 던지지 않는다는 것도 본다 - 던지면 이미 커밋된 탈퇴가 500으로
 * 답한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class IdpUnlinkerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String ADMIN_KEY = "kakaoAdminKey0123456789";
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

    private IdpUnlinker unlinker(@Nullable String adminKey, @Nullable String naverClientSecret) {
        AccenturyProperties.Auth base = PropertiesFixture.auth();
        String url = server.url("").toString().replaceAll("/$", "");
        AccenturyProperties.Auth auth = new AccenturyProperties.Auth(base.jwtSecret(), base.issuer(),
                base.accessTokenTtl(), base.refreshTokenTtl(), base.rateLimitPerMinute(), false, null, null, "1234567",
                NAVER_CLIENT_ID, naverClientSecret, null, null, null, base.googleJwksUrl(), base.appleJwksUrl(),
                url, url, url, url, Duration.ofSeconds(2), base.privacyPolicyVersion());
        return new IdpUnlinker(auth, adminKey, JsonMapper.builder().build());
    }

    private IdpUnlinker unlinker() {
        return unlinker(ADMIN_KEY, NAVER_CLIENT_SECRET);
    }

    private void enqueueJson(int code, String body) {
        server.enqueue(new MockResponse.Builder().code(code).setHeader("Content-Type", "application/json")
                .body(body).build());
    }

    private RecordedRequest take() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "요청이 나가지 않았다");
        return request;
    }

    // === 카카오 ===

    @Test
    void 카카오는_어드민_키와_회원번호로_연결_해제를_부른다(CapturedOutput output) throws Exception {
        enqueueJson(200, "{\"id\": 4242}");

        unlinker().unlinkKakao("4242", USER_ID);

        RecordedRequest request = take();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/user/unlink", request.getUrl().encodedPath());
        assertEquals("KakaoAK " + ADMIN_KEY, request.getHeaders().get("Authorization"));
        assertTrue(request.getHeaders().get("Content-Type").startsWith("application/x-www-form-urlencoded"));
        String body = request.getBody().utf8();
        assertTrue(body.contains("target_id_type=user_id"), body);
        assertTrue(body.contains("target_id=4242"), body);
        assertTrue(output.getAll().contains("카카오 연결 해제 userId=" + USER_ID));
        assertFalse(output.getAll().contains(ADMIN_KEY), "어드민 키가 로그에 남으면 안 된다");
    }

    @Test
    void 카카오가_거절해도_던지지_않고_WARN만_남긴다(CapturedOutput output) {
        // 이미 연결이 끊긴 사용자는 카카오가 400으로 답한다.
        enqueueJson(400, "{\"msg\": \"NotRegisteredUserException\", \"code\": -101}");

        unlinker().unlinkKakao("4242", USER_ID);

        assertTrue(output.getAll().contains("카카오 연결 해제 실패 userId=" + USER_ID));
    }

    @Test
    void 카카오_장애에도_던지지_않는다(CapturedOutput output) {
        enqueueJson(503, "{}");

        unlinker().unlinkKakao("4242", USER_ID);

        assertTrue(output.getAll().contains("카카오 연결 해제 실패 userId=" + USER_ID));
    }

    @Test
    void 어드민_키가_없거나_회원번호가_숫자가_아니면_카카오를_부르지_않는다(CapturedOutput output) throws Exception {
        unlinker(null, NAVER_CLIENT_SECRET).unlinkKakao("4242", USER_ID);
        unlinker("unset-put-parameter-after-apply", NAVER_CLIENT_SECRET).unlinkKakao("4242", USER_ID);
        // 가짜 IdP의 sub가 이런 모양이다 - 로컬과 테스트에서 카카오로 나가지 않는다.
        unlinker().unlinkKakao("sub-not-a-number", USER_ID);

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "카카오로 요청이 나가면 안 된다");
        assertTrue(output.getAll().contains("카카오 연결 해제 건너뜀 - 미설정"));
        assertTrue(output.getAll().contains("카카오 연결 해제 건너뜀 - 회원번호 형식이 아니다"));
    }

    /**
     * 어드민 키가 자리 표시 값이나 빈 문자열이어도 기동 때 경고한다 (PR #70 리뷰) - 걸러 낸 필드가 아니라 인자를 보면
     * 이 두 경우는 null이 아니라서 경고 없이 뜨고, 설정 누락이 탈퇴 때에야 드러난다.
     */
    @Test
    void 어드민_키가_자리_표시_값이거나_비어_있어도_기동_때_경고한다(CapturedOutput output) {
        for (String unset : new String[]{null, "unset-put-parameter-after-apply", "  "}) {
            int before = count(output.getAll(), "카카오 연결 해제 미설정");

            unlinker(unset, NAVER_CLIENT_SECRET);

            assertEquals(before + 1, count(output.getAll(), "카카오 연결 해제 미설정"), "어드민 키 값: " + unset);
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    // === 네이버 ===

    @Test
    void 네이버는_토큰을_교환해_주인을_확인한_뒤_삭제를_부른다(CapturedOutput output) throws Exception {
        enqueueJson(200, "{\"access_token\": \"exchanged-access\", \"token_type\": \"bearer\", \"expires_in\": \"3600\"}");
        enqueueJson(200, "{\"resultcode\": \"00\", \"message\": \"success\", \"response\": {\"id\": \"naver-user-1\"}}");
        enqueueJson(200, "{\"access_token\": \"exchanged-access\", \"result\": \"success\"}");

        unlinker().unlinkNaver("sdk-refresh-token", "naver-user-1", USER_ID);

        RecordedRequest exchange = take();
        assertEquals("/oauth2.0/token", exchange.getUrl().encodedPath());
        String exchangeBody = exchange.getBody().utf8();
        assertTrue(exchangeBody.contains("grant_type=refresh_token"), exchangeBody);
        assertTrue(exchangeBody.contains("refresh_token=sdk-refresh-token"), exchangeBody);

        RecordedRequest me = take();
        assertEquals("/v1/nid/me", me.getUrl().encodedPath());
        assertEquals("Bearer exchanged-access", me.getHeaders().get("Authorization"));

        RecordedRequest delete = take();
        assertEquals("POST", delete.getMethod());
        assertEquals("/oauth2.0/token", delete.getUrl().encodedPath());
        String deleteBody = delete.getBody().utf8();
        assertTrue(deleteBody.contains("grant_type=delete"), deleteBody);
        assertTrue(deleteBody.contains("client_id=" + NAVER_CLIENT_ID), deleteBody);
        assertTrue(deleteBody.contains("client_secret=" + NAVER_CLIENT_SECRET), deleteBody);
        assertTrue(deleteBody.contains("access_token=exchanged-access"), deleteBody);
        assertTrue(deleteBody.contains("service_provider=NAVER"), deleteBody);

        String logs = output.getAll();
        assertTrue(logs.contains("네이버 연결 해제 userId=" + USER_ID));
        for (String secret : new String[]{"sdk-refresh-token", "exchanged-access", NAVER_CLIENT_SECRET}) {
            assertFalse(logs.contains(secret), "로그에 새면 안 되는 값: " + secret);
        }
    }

    @Test
    void 네이버_토큰의_주인이_탈퇴한_계정이_아니면_삭제하지_않는다(CapturedOutput output) throws Exception {
        enqueueJson(200, "{\"access_token\": \"exchanged-access\"}");
        enqueueJson(200, "{\"resultcode\": \"00\", \"response\": {\"id\": \"someone-else\"}}");

        unlinker().unlinkNaver("sdk-refresh-token", "naver-user-1", USER_ID);

        take();
        take();
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "삭제 요청이 나가면 안 된다");
        assertTrue(output.getAll().contains("토큰이 탈퇴한 계정의 것이 아니다"));
    }

    /** 로그인과 같은 검사를 쓴다 (PR #70 리뷰) - 프로필 조회가 실패({@code resultcode}가 00이 아님)면 삭제로 가지 않는다. */
    @Test
    void 네이버_프로필_조회가_실패하면_삭제하지_않는다(CapturedOutput output) throws Exception {
        enqueueJson(200, "{\"access_token\": \"exchanged-access\"}");
        enqueueJson(200, "{\"resultcode\": \"024\", \"message\": \"Authentication failed\"}");

        unlinker().unlinkNaver("sdk-refresh-token", "naver-user-1", USER_ID);

        take();
        take();
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "삭제 요청이 나가면 안 된다");
        assertTrue(output.getAll().contains("네이버 연결 해제 실패 userId=" + USER_ID + " code=AUTH_IDP_TOKEN_INVALID"));
    }

    @Test
    void 네이버_토큰이_없거나_설정이_없으면_부르지_않는다(CapturedOutput output) throws Exception {
        unlinker().unlinkNaver(null, "naver-user-1", USER_ID);
        unlinker().unlinkNaver("  ", "naver-user-1", USER_ID);
        unlinker(ADMIN_KEY, null).unlinkNaver("sdk-refresh-token", "naver-user-1", USER_ID);

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "네이버로 요청이 나가면 안 된다");
        assertTrue(output.getAll().contains("네이버 연결 해제 건너뜀 - naverRefreshToken 없음"));
        assertTrue(output.getAll().contains("네이버 연결 해제 건너뜀 - 미설정"));
    }

    @Test
    void 네이버가_교환을_거절하거나_삭제가_실패해도_던지지_않는다(CapturedOutput output) {
        // 만료됐거나 다른 앱의 refresh token - 네이버는 200에 오류를 싣는다.
        enqueueJson(200, "{\"error\": \"invalid_request\", \"error_description\": \"invalid refresh_token\"}");
        unlinker().unlinkNaver("expired-refresh-token", "naver-user-1", USER_ID);

        enqueueJson(200, "{\"access_token\": \"exchanged-access\"}");
        enqueueJson(200, "{\"resultcode\": \"00\", \"response\": {\"id\": \"naver-user-1\"}}");
        enqueueJson(200, "{\"error\": \"invalid_request\", \"error_description\": \"fail\"}");
        unlinker().unlinkNaver("sdk-refresh-token", "naver-user-1", USER_ID);

        String logs = output.getAll();
        assertTrue(logs.contains("네이버 연결 해제 실패 userId=" + USER_ID + " code=AUTH_IDP_TOKEN_INVALID"));
        assertTrue(logs.contains("네이버 연결 해제 실패 userId=" + USER_ID + " error=invalid_request"));
    }
}
