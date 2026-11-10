package app.accentury.backend.auth;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.RateLimits;
import app.accentury.backend.session.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게스트 계정의 실행 가능한 명세 (KAN-329, 명세서 §3.21) - 실제 PostgreSQL과 Redis 위에서 돈다.
 * <p>
 * 발급 상한이 IP당 하루 단위라 테스트마다 다른 IP로 부른다 - 한 IP를 같이 쓰면 앞 테스트가 뒤 테스트의 몫을 깎는다.
 * 게스트의 번역은 {@code TranslationApiTest}가, 돌아올 수 없는 게스트의 파기는 {@link GuestAccountRetentionTest}가 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
@ExtendWith(OutputCaptureExtension.class)
class GuestApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;
    private static final int DAILY_LIMIT = AccenturyProperties.Auth.GUEST_DAILY_LIMIT_PER_IP;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TestSessionRepository sessions;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private GuestQuota guestQuota;

    @Autowired
    private IdpVerifiers idpVerifiers;

    @Autowired
    private AccessTokens accessTokens;

    @Autowired
    private RateLimits rateLimits;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccenturyProperties properties;

    // === 발급 (AC 1) ===

    @Test
    void 동의하고_부르면_토큰_없이도_게스트_계정과_토큰_쌍을_받는다() throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").value(startsWith("eyJ")))
                .andExpect(jsonPath("$.refreshToken").value(startsWith("rt_")))
                .andExpect(jsonPath("$.accessTokenExpiresInSec").value(1800))
                .andExpect(jsonPath("$.user.provider").value("GUEST"))
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.user.region").doesNotExist()));

        AppUser user = users.findActive(UUID.fromString(guest.get("user").get("id").asString())).orElseThrow();
        assertTrue(user.isGuest());
        assertNotNull(user.privacyConsentAt());
        assertEquals(POLICY_VERSION, user.privacyPolicyVersion());
        assertNull(user.region());
        assertFalse(user.isProfileComplete());
        assertFalse(user.hasVoiceConsent(), "게스트는 음성 저장 동의가 없다");
    }

    @Test
    void 부를_때마다_새_계정이다() throws Exception {
        String ip = uniqueIp();

        String first = body(guest(ip, true, POLICY_VERSION)).get("user").get("id").asString();
        String second = body(guest(ip, true, POLICY_VERSION)).get("user").get("id").asString();

        assertNotEquals(first, second);
    }

    @ParameterizedTest
    @CsvSource(value = {"false, CURRENT", "NULL, CURRENT", "true, 2026-09-15", "true, NULL"}, nullValues = "NULL")
    void 동의가_없거나_버전이_다르면_400이고_계정도_하루_상한도_쓰지_않는다(Boolean consent, String version) throws Exception {
        String ip = uniqueIp();
        long before = users.count();

        guest(ip, consent, "CURRENT".equals(version) ? POLICY_VERSION : version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CONSENT_REQUIRED"));

        assertEquals(before, users.count());
        assertNull(redis.opsForValue().get(guestQuota.key(ip, today())), "동의가 없는 요청은 하루 상한을 깎지 않는다");
    }

    @Test
    void 본문이_없으면_400이다() throws Exception {
        mockMvc.perform(post("/v0/auth/guest").with(from(uniqueIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 로그인의_provider로는_GUEST를_받지_않는다() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "GUEST");
        request.put("accessToken", "fake:guest-via-login");
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", POLICY_VERSION);

        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // === IP당 하루 상한 (AC 4) ===

    @Test
    void 같은_IP의_하루_상한을_넘으면_429이고_다른_IP는_영향이_없다() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < DAILY_LIMIT; i++) {
            guest(ip, true, POLICY_VERSION).andExpect(status().isOk());
        }
        long before = users.count();

        guest(ip, true, POLICY_VERSION)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_GUEST_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertEquals(before, users.count(), "상한을 넘은 요청은 계정을 만들지 않는다");
        assertEquals(String.valueOf(DAILY_LIMIT), redis.opsForValue().get(guestQuota.key(ip, today())),
                "넘은 요청은 카운터를 올린 채 두지 않는다");
        guest(uniqueIp(), true, POLICY_VERSION).andExpect(status().isOk());
    }

    /**
     * IPv6는 가입자 한 명에게 /64가 통째로 간다 (PR #69 리뷰) - 주소 단위로 세면 주소만 바꿔 상한을 끝없이 피한다.
     */
    @Test
    void IPv6는_같은_64비트_대역이_한_통이고_주소를_바꿔도_상한을_피하지_못한다() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String prefix = "2001:db8:" + Integer.toHexString(random.nextInt(1, 0x10000)) + ":"
                + Integer.toHexString(random.nextInt(1, 0x10000));
        for (int i = 1; i <= DAILY_LIMIT; i++) {
            guest(prefix + "::" + Integer.toHexString(i), true, POLICY_VERSION).andExpect(status().isOk());
        }

        guest(prefix + ":aaaa:bbbb:cccc:dddd", true, POLICY_VERSION)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_GUEST_LIMIT_EXCEEDED"));

        guest(prefix + "f::1", true, POLICY_VERSION).andExpect(status().isOk());
    }

    @Test
    void 통은_IPv4는_주소이고_IPv6는_앞_64비트다() {
        assertEquals(GuestQuota.bucket("2001:db8:1:2::1"), GuestQuota.bucket("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
        assertNotEquals(GuestQuota.bucket("2001:db8:1:2::1"), GuestQuota.bucket("2001:db8:1:3::1"));
        assertNotEquals(GuestQuota.bucket("10.1.2.3"), GuestQuota.bucket("10.1.2.4"));
        assertEquals("10.1.2.3", GuestQuota.bucket("::ffff:10.1.2.3"), "IPv4를 감싼 IPv6 주소는 IPv4로 센다");
        assertEquals("not-an-ip", GuestQuota.bucket("not-an-ip"), "읽지 못한 값도 한 통이다 - 상한을 풀지 않는다");
    }

    /**
     * 키에서 IP를 되살릴 수 없어야 한다 (PR #69 리뷰). 솔트 없는 SHA-256은 IPv4 전부를 대입하면 몇 초 만에 풀린다.
     */
    @Test
    void 키에는_IP_원문도_솔트_없는_해시도_없다() {
        String ip = "203.0.113.77";

        String key = guestQuota.key(ip, today());

        assertFalse(key.contains(ip));
        assertFalse(key.contains(RefreshTokens.hash(ip)), "SHA-256(IP)가 그대로 키에 있으면 전수 대입으로 IP가 나온다");
        assertEquals(key, guestQuota.key(ip, today()), "같은 IP와 날짜는 같은 키다");
        assertNotEquals(key, new GuestQuota(redis, java.time.Clock.systemUTC(), new byte[32]).key(ip, today()),
                "비밀값이 다르면 키도 다르다");
    }

    /**
     * 토큰을 주지 못한 발급은 하루 상한에서 뺀다 (PR #69 리뷰) - 서버 장애 동안의 재시도가 그 IP의 하루 몫을 다 쓰지
     * 않게 한다. Refresh 발급이 실패한 경우를 본다: 계정 행은 이미 커밋됐고 정리 잡이 걷어 간다.
     */
    @Test
    void 토큰_발급이_실패하면_잡았던_하루_상한_칸을_돌려준다() {
        String ip = uniqueIp();
        RefreshTokens down = new RefreshTokens(redis, properties) {
            @Override
            public String issue(UUID userId) {
                throw new ApiException(ErrorCode.AUTH_STORE_UNAVAILABLE);
            }
        };
        AuthService failing = new AuthService(idpVerifiers, users, accessTokens, down, rateLimits, guestQuota,
                transactionTemplate, properties);

        ApiException e = assertThrows(ApiException.class,
                () -> failing.guest(new GuestRequest(true, POLICY_VERSION), ip));

        assertEquals(ErrorCode.AUTH_STORE_UNAVAILABLE, e.code());
        assertEquals("0", redis.opsForValue().get(guestQuota.key(ip, today())));
    }

    // === 게스트가 쓰는 계정 API ===

    @Test
    void 게스트_토큰은_내_계정을_보고_refresh로_회전된다() throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION));

        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.provider").value("GUEST"))
                .andExpect(jsonPath("$.profileStatus").value("INCOMPLETE"));

        JsonNode rotated = body(mockMvc.perform(post("/v0/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", guest.get("refreshToken").asString()))))
                .andExpect(status().isOk()));
        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, bearer(rotated)))
                .andExpect(status().isOk());
    }

    @Test
    void 게스트도_탈퇴할_수_있고_그_뒤의_토큰은_401이다() throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION));
        UUID userId = UUID.fromString(guest.get("user").get("id").asString());

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isNoContent());

        assertTrue(users.findActive(userId).isEmpty());
        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게스트도_로그아웃할_수_있다() throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION));

        mockMvc.perform(post("/v0/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", guest.get("refreshToken").asString()))))
                .andExpect(status().isNoContent());
    }

    // === 로그인 계정 전용 API (AC 3) ===

    /**
     * 게스트는 사투리 번역기만 쓴다 (2026-11-10 결정). 없는 id를 넣은 경로도 404가 아니라 403이다 - 게스트 판정이 핸들러보다
     * 먼저라 자원이 있는지조차 알려 주지 않는다.
     */
    @ParameterizedTest
    @CsvSource({
            "GET, /v0/learning/word-sets",
            "GET, /v0/learning/word-sets/any-set",
            "POST, /v0/learning/word-sets/any-set/attempts",
            "POST, /v0/learning/word-attempts/any-attempt/complete",
            "GET, /v0/learning/intonation-courses",
            "GET, /v0/learning/intonation-courses/any-course",
            "GET, /v0/learning/intonation-attempts/any-attempt",
            "GET, /v0/learning/home",
            "GET, /v0/learning/attendance",
            "PUT, /v0/users/me/profile",
            "PUT, /v0/users/me/voice-consent",
            "DELETE, /v0/users/me/voice-consent",
    })
    void 게스트_토큰으로_로그인_계정_전용_API를_부르면_403이다(String method, String path) throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION));

        mockMvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_GUEST_NOT_ALLOWED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void 게스트_토큰을_실은_세션_생성은_403이고_토큰_없는_익명_세션은_된다() throws Exception {
        JsonNode guest = body(guest(uniqueIp(), true, POLICY_VERSION));
        long before = sessions.count();

        mockMvc.perform(post("/v0/sessions").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_GUEST_NOT_ALLOWED"));
        assertEquals(before, sessions.count());

        // 게스트의 레벨테스트는 이 경로다 - 계정에 귀속되지 않는 익명 세션.
        String sessionId = body(mockMvc.perform(post("/v0/sessions")).andExpect(status().isCreated()))
                .get("sessionId").asString();
        assertNull(sessions.findById(sessionId).orElseThrow().userId());
    }

    // === 로그 (§2.6) ===

    @Test
    void 로그에_토큰_원문과_IP가_남지_않는다(CapturedOutput output) throws Exception {
        String ip = uniqueIp();
        JsonNode guest = body(guest(ip, true, POLICY_VERSION));

        String logs = output.getAll();
        assertTrue(logs.contains("게스트 발급 userId=" + guest.get("user").get("id").asString()));
        for (String secret : new String[]{guest.get("accessToken").asString(), guest.get("refreshToken").asString(), ip}) {
            assertFalse(logs.contains(secret), "로그에 새면 안 되는 값: " + secret.substring(0, Math.min(12, secret.length())));
        }
    }

    private ResultActions guest(String ip, Boolean consent, String version) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        if (consent != null) {
            request.put("privacyConsent", consent);
        }
        if (version != null) {
            request.put("privacyPolicyVersion", version);
        }
        return mockMvc.perform(post("/v0/auth/guest").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    /** 요청의 접속 IP를 정한다 - 테스트 프로파일에는 신뢰 프록시가 없어 이 값이 그대로 요청 제한의 키다. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private static String uniqueIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asString();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
