package app.accentury.backend.auth;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.session.TestSession;
import app.accentury.backend.session.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 인증 API 전 구간 (KAN-223 AC, 명세서 §3.1, §3.9~§3.13) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * IdP 자체의 검증(서명, aud, nonce, app_id)은 {@link IdTokenVerifiersTest}와 {@link RestIdpVerifiersTest}가 보고,
 * 여기는 가짜 IdP({@code fake:<sub>})로 그 뒤의 흐름 - 가입, 재로그인, 프로필, 토큰 회전, 세션 귀속 - 을 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
@ExtendWith(OutputCaptureExtension.class)
class AuthApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = "2026-09-24";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TestSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    // === 로그인 = 가입 겸용 (§3.9) ===

    @Test
    void 처음_로그인하면_가입되고_INCOMPLETE와_토큰_쌍을_받는다() throws Exception {
        String sub = uniqueSub();

        login("KAKAO", sub, true)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileStatus").value("INCOMPLETE"))
                .andExpect(jsonPath("$.accessTokenExpiresInSec").value(1800))
                .andExpect(jsonPath("$.accessToken").value(startsWith("eyJ")))
                .andExpect(jsonPath("$.refreshToken").value(startsWith("rt_")))
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.region").doesNotExist());

        AppUser user = users.findByProviderAndProviderUserId(Provider.KAKAO, sub).orElseThrow();
        assertNotNull(user.privacyConsentAt());
        assertEquals(POLICY_VERSION, user.privacyPolicyVersion());
        assertFalse(user.isProfileComplete());
    }

    @Test
    void 동의_없는_가입은_400이고_계정이_생기지_않는다() throws Exception {
        String sub = uniqueSub();

        login("GOOGLE", sub, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CONSENT_REQUIRED"));

        assertTrue(users.findByProviderAndProviderUserId(Provider.GOOGLE, sub).isEmpty());
    }

    @Test
    void 재로그인은_같은_계정으로_이어지고_동의를_다시_묻지_않는다() throws Exception {
        String sub = uniqueSub();
        String firstId = body(login("NAVER", sub, true)).get("user").get("id").asString();

        JsonNode again = body(login("NAVER", sub, false).andExpect(status().isOk()));

        assertEquals(firstId, again.get("user").get("id").asString());
        assertFalse(again.get("isNewUser").asBoolean());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from app_user where provider = 'NAVER' and provider_user_id = ?", Integer.class, sub));
    }

    @Test
    void 같은_사용자_id라도_IdP가_다르면_별개_계정이다() throws Exception {
        String sub = uniqueSub();

        String kakaoId = body(login("KAKAO", sub, true)).get("user").get("id").asString();
        String googleId = body(login("GOOGLE", sub, true)).get("user").get("id").asString();

        assertNotEquals(kakaoId, googleId);
    }

    @Test
    void 네_IdP_모두_가입과_재로그인이_된다() throws Exception {
        for (String provider : new String[]{"GOOGLE", "KAKAO", "NAVER", "APPLE"}) {
            String sub = uniqueSub();
            String id = body(login(provider, sub, true).andExpect(status().isOk())).get("user").get("id").asString();
            assertEquals(id, body(login(provider, sub, false)).get("user").get("id").asString(), provider);
        }
    }

    @Test
    void 요청_형식이_틀리면_IdP에_묻기_전에_400이다() throws Exception {
        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"FACEBOOK\", \"accessToken\": \"fake:x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 카카오는 accessToken이지 idToken이 아니다.
        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"KAKAO\", \"idToken\": \"fake:x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 애플은 원문 nonce가 필수다.
        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"APPLE\", \"idToken\": \"fake:x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/v0/auth/login"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 가짜_IdP라도_sub_형식이_틀리면_401이다() throws Exception {
        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"KAKAO\", \"accessToken\": \"fake:한글\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_IDP_TOKEN_INVALID"));
    }

    // === 프로필 (§3.10, §3.11) ===

    @Test
    void 다섯_항목을_채우면_COMPLETE가_되고_내_정보에서도_그렇다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();

        putProfile(access, profile())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.profileStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.birthDate").value("1999-03-02"))
                .andExpect(jsonPath("$.user.gender").value("MALE"))
                .andExpect(jsonPath("$.user.region").value("GYEONGNAM"));

        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.user.name").value("박유현"));
    }

    @Test
    void 완료된_프로필에_다시_보내면_값이_갱신되고_완료_시각은_처음_것이다() throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String access = login.get("accessToken").asString();
        UUID userId = UUID.fromString(login.get("user").get("id").asString());
        putProfile(access, profile()).andExpect(status().isOk());
        var firstCompletedAt = users.findById(userId).orElseThrow().profileCompletedAt();

        Map<String, Object> changed = profile();
        changed.put("region", "SEOUL");
        putProfile(access, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.region").value("SEOUL"));

        assertEquals(firstCompletedAt, users.findById(userId).orElseThrow().profileCompletedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "name", "birthDate", "gender", "region"})
    void 한_항목이라도_빠지면_400이고_완료되지_않는다(String missing) throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();
        Map<String, Object> request = profile();
        request.remove(missing);

        putProfile(access, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        me(access).andExpect(jsonPath("$.profileStatus").value("INCOMPLETE"));
    }

    @Test
    void 형식이_틀린_항목은_400이다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();

        for (Map.Entry<String, Object> wrong : Map.<String, Object>of(
                "email", "not-an-email",
                "name", "가".repeat(51),
                "birthDate", "1999-02-30",
                "gender", "OTHER",
                "region", "BUSAN").entrySet()) {
            Map<String, Object> request = profile();
            request.put(wrong.getKey(), wrong.getValue());
            putProfile(access, request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        Map<String, Object> future = profile();
        future.put("birthDate", LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString());
        putProfile(access, future).andExpect(status().isBadRequest());
    }

    @Test
    void 만_14세_미만은_400이고_생년월일도_남지_않는다() throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String access = login.get("accessToken").asString();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Map<String, Object> child = profile();
        child.put("birthDate", today.minusYears(14).plusDays(1).toString());

        putProfile(access, child)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_UNDER_AGE"));

        AppUser user = users.findById(UUID.fromString(login.get("user").get("id").asString())).orElseThrow();
        assertNull(user.birthDate());
        assertFalse(user.isProfileComplete());
    }

    @Test
    void 만_14세_생일_당일부터_가입할_수_있다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();
        Map<String, Object> fourteen = profile();
        fourteen.put("birthDate", LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(14).toString());

        putProfile(access, fourteen).andExpect(status().isOk());
    }

    @Test
    void Access_토큰이_없거나_틀리면_401이다() throws Exception {
        mockMvc.perform(get("/v0/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(put("/v0/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile())))
                .andExpect(status().isUnauthorized());
    }

    // === Refresh 회전과 로그아웃 (§3.12, §3.13) ===

    @Test
    void refresh는_새_쌍을_주고_낸_토큰은_다시_못_쓴다() throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String first = login.get("refreshToken").asString();

        JsonNode rotated = body(refresh(first)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        String second = rotated.get("refreshToken").asString();
        assertNotEquals(first, second);
        me(rotated.get("accessToken").asString()).andExpect(status().isOk());

        // 이미 회전된 토큰의 재사용 = 복사본이 있다 - 그 로그인의 패밀리 전체가 폐기된다.
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void 재사용_감지는_다른_기기의_로그인을_건드리지_않는다() throws Exception {
        String sub = uniqueSub();
        String deviceA = body(login("KAKAO", sub, true)).get("refreshToken").asString();
        String deviceB = body(login("KAKAO", sub, false)).get("refreshToken").asString();
        body(refresh(deviceA));

        refresh(deviceA).andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));

        refresh(deviceB).andExpect(status().isOk());
    }

    @Test
    void 패밀리_집합이_사라진_토큰은_되살아나지_않고_401이다() throws Exception {
        // 메모리 축출이나 부분 삭제로 패밀리 집합만 없어진 상태 (Codex 리뷰 P1) - 그 토큰으로 패밀리를 다시 만들면
        // 로그아웃이나 재사용 폐기가 새어 나간다.
        String refreshToken = body(login("KAKAO", uniqueSub(), true)).get("refreshToken").asString();
        String tokenKey = RefreshTokens.TOKEN_KEY + RefreshTokens.hash(refreshToken);
        Object family = redis.opsForHash().get(tokenKey, "f");
        redis.delete(RefreshTokens.FAMILY_KEY + family);

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(tokenKey)), "남은 토큰 키도 지운다");
    }

    @Test
    void 회전할_때_만료된_멤버를_패밀리_집합에서_걷어_낸다() throws Exception {
        // 계속 쓰는 로그인은 패밀리 TTL이 늘어나므로, 걷어 내지 않으면 집합이 끝없이 커진다 (Codex 2차 리뷰 P2).
        String first = body(login("KAKAO", uniqueSub(), true)).get("refreshToken").asString();
        String firstKey = RefreshTokens.TOKEN_KEY + RefreshTokens.hash(first);
        String familyKey = RefreshTokens.FAMILY_KEY + redis.opsForHash().get(firstKey, "f");
        String second = body(refresh(first)).get("refreshToken").asString();
        redis.delete(firstKey);    // 첫 토큰의 TTL이 끝난 것과 같다.

        body(refresh(second).andExpect(status().isOk()));

        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(familyKey, RefreshTokens.hash(first))));
        assertEquals(2L, redis.opsForSet().size(familyKey), "살아 있는 둘(회전된 두 번째, 새 세 번째)만 남는다");
    }

    @Test
    void 모르는_Refresh는_401이고_빠지면_400이다() throws Exception {
        refresh("rt_" + "A".repeat(43))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        refresh("not-a-refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        mockMvc.perform(post("/v0/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 로그아웃_뒤_같은_Refresh는_401이다() throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String refreshToken = login.get("refreshToken").asString();

        logout(login.get("accessToken").asString(), refreshToken).andExpect(status().isNoContent());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void 남의_Refresh로_로그아웃해도_204이고_그_토큰은_살아_있다() throws Exception {
        JsonNode me = body(login("KAKAO", uniqueSub(), true));
        String othersRefresh = body(login("KAKAO", uniqueSub(), true)).get("refreshToken").asString();

        logout(me.get("accessToken").asString(), othersRefresh).andExpect(status().isNoContent());

        refresh(othersRefresh).andExpect(status().isOk());
    }

    @Test
    void 로그아웃은_Access_토큰이_필요하다() throws Exception {
        String refreshToken = body(login("KAKAO", uniqueSub(), true)).get("refreshToken").asString();

        mockMvc.perform(post("/v0/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        refresh(refreshToken).andExpect(status().isOk());
    }

    // === 세션 생성의 계정 귀속 (§3.1, FR-AC-11) ===

    @Test
    void Access_토큰으로_만든_세션은_계정에_귀속되고_출신지역은_계정의_값이다() throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String access = login.get("accessToken").asString();
        putProfile(access, profile()).andExpect(status().isOk());

        // 본문 region은 무시한다 - 코드 밖의 값이어도 400이 아니다.
        String sessionId = body(mockMvc.perform(post("/v0/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\": \"NOWHERE\", \"client\": {\"platform\": \"ANDROID\"}}"))
                .andExpect(status().isCreated()))
                .get("sessionId").asString();

        TestSession session = sessions.findById(sessionId).orElseThrow();
        assertEquals(UUID.fromString(login.get("user").get("id").asString()), session.userId());
        assertEquals("GYEONGNAM", session.region());
    }

    @Test
    void 프로필이_미완료인_계정은_403이고_세션이_생기지_않는다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();
        long before = sessions.count();

        mockMvc.perform(post("/v0/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PROFILE_INCOMPLETE"));

        assertEquals(before, sessions.count());
    }

    @Test
    void 위조된_Access_토큰은_익명으로_떨어지지_않고_401이다() throws Exception {
        long before = sessions.count();

        mockMvc.perform(post("/v0/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));

        assertEquals(before, sessions.count());
    }

    @Test
    void 헤더가_없으면_지금처럼_익명_세션이다() throws Exception {
        String sessionId = body(mockMvc.perform(post("/v0/sessions")).andExpect(status().isCreated()))
                .get("sessionId").asString();

        assertNull(sessions.findById(sessionId).orElseThrow().userId());
    }

    @Test
    void 로그인한_앱의_재응시는_본문의_이전_세션_토큰으로_폐기한다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();
        putProfile(access, profile()).andExpect(status().isOk());
        JsonNode first = body(createSession(access, null));

        JsonNode second = body(createSession(access, first.get("sessionToken").asString()));

        assertTrue(sessions.findById(first.get("sessionId").asString()).isEmpty(), "이전 세션은 즉시 폐기된다");
        assertNotNull(sessions.findById(second.get("sessionId").asString()).orElseThrow().userId());
    }

    @Test
    void 본문의_이전_세션_토큰이_무효여도_조용히_새_세션을_준다() throws Exception {
        String access = body(login("KAKAO", uniqueSub(), true)).get("accessToken").asString();
        putProfile(access, profile()).andExpect(status().isOk());

        createSession(access, "st_never-issued-token");
        createSession(null, "st_never-issued-token");
    }

    // === 로그 (§2.6, NFR-SC-07) ===

    @Test
    void 로그에_이메일_이름_토큰_원문이_남지_않는다(CapturedOutput output) throws Exception {
        JsonNode login = body(login("KAKAO", uniqueSub(), true));
        String access = login.get("accessToken").asString();
        String refreshToken = login.get("refreshToken").asString();
        Map<String, Object> request = profile();
        request.put("email", "leak-check@example.com");
        request.put("name", "로그검사이름");
        putProfile(access, request).andExpect(status().isOk());
        JsonNode rotated = body(refresh(refreshToken));
        refresh(refreshToken);    // 재사용 경로의 warn 로그도 본다.
        createSession(access, null);
        logout(access, rotated.get("refreshToken").asString());

        String logs = output.getAll();
        assertTrue(logs.contains("로그인 provider=KAKAO"), "로그인 로그는 남아야 한다");
        for (String secret : new String[]{"leak-check@example.com", "로그검사이름", access, refreshToken,
                rotated.get("refreshToken").asString(), rotated.get("accessToken").asString()}) {
            assertFalse(logs.contains(secret), "로그에 새면 안 되는 값: " + secret.substring(0, Math.min(12, secret.length())));
        }
    }

    // === 도우미 ===

    private static String uniqueSub() {
        return "sub-" + UUID.randomUUID();
    }

    private ResultActions login(String provider, String sub, boolean consent) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", provider);
        if ("GOOGLE".equals(provider) || "APPLE".equals(provider)) {
            request.put("idToken", "fake:" + sub);
        } else {
            request.put("accessToken", "fake:" + sub);
        }
        if ("APPLE".equals(provider)) {
            request.put("nonce", "raw-nonce");
        }
        if (consent) {
            request.put("privacyConsent", true);
            request.put("privacyPolicyVersion", POLICY_VERSION);
        }
        return mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static Map<String, Object> profile() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("email", "user@example.com");
        request.put("name", "박유현");
        request.put("birthDate", "1999-03-02");
        request.put("gender", "MALE");
        request.put("region", "GYEONGNAM");
        return request;
    }

    private ResultActions putProfile(String access, Map<String, Object> request) throws Exception {
        return mockMvc.perform(put("/v0/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions me(String access) throws Exception {
        return mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/v0/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions logout(String access, String refreshToken) throws Exception {
        return mockMvc.perform(post("/v0/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions createSession(String access, String previousSessionToken) throws Exception {
        var request = post("/v0/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(previousSessionToken == null ? "{}"
                        : objectMapper.writeValueAsString(Map.of("previousSessionToken", previousSessionToken)));
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andExpect(status().isCreated());
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
