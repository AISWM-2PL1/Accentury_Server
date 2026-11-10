package app.accentury.backend.auth;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 돌아올 수 없는 게스트 계정의 파기 (KAN-329 AC 6, 명세서 §3.21, §5.5) - 실제 PostgreSQL과 Redis 위에서 돈다.
 * <p>
 * "30일 동안 쓰지 않음"은 Redis의 수명이 만드는 상태라 시계를 돌려 재현하지 못한다. 그 끝 상태(사용자 색인이 없음)를
 * 로그아웃이나 키 삭제로 만들어 본다 - 잡이 보는 것도 색인의 유무뿐이다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class GuestAccountRetentionTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GuestAccountRetention retention;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private AccenturyProperties properties;

    @Test
    void 로그인이_살아_있는_게스트는_여유가_지나도_지우지_않는다() throws Exception {
        JsonNode guest = guest();

        retention.purgeUnreachable(afterGrace());

        assertTrue(users.findActive(userId(guest)).isPresent());
        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isOk());
    }

    @Test
    void Refresh가_만료된_게스트는_탈퇴와_같이_파기되고_그_토큰은_401이다() throws Exception {
        JsonNode guest = guest();
        UUID userId = userId(guest);
        // 30일 동안 회전이 없으면 패밀리와 사용자 색인이 함께 만료된다 - 그 끝 상태다.
        redis.delete(RefreshTokens.USER_KEY + userId);

        int purged = retention.purgeUnreachable(afterGrace());

        assertTrue(purged >= 1);
        AppUser gone = users.findById(userId).orElseThrow();
        assertNotNull(gone.deletedAt());
        assertEquals("deleted:" + userId, gone.providerUserId());
        mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃한_게스트는_다음_회차에_파기된다() throws Exception {
        JsonNode guest = guest();
        mockMvc.perform(post("/v0/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", guest.get("refreshToken").asString()))))
                .andExpect(status().isNoContent());

        retention.purgeUnreachable(afterGrace());

        assertTrue(users.findActive(userId(guest)).isEmpty());
    }

    @Test
    void 갓_만든_게스트는_색인이_없어도_여유_동안_지우지_않는다() throws Exception {
        JsonNode guest = guest();
        redis.delete(RefreshTokens.USER_KEY + userId(guest));

        // 발급이 계정을 커밋하고 Refresh를 내기 전의 순간과 같은 상태다.
        retention.purgeUnreachable(Instant.now());

        assertTrue(users.findActive(userId(guest)).isPresent());
    }

    @Test
    void 소셜_계정은_로그인이_없어도_지우지_않는다() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:retention-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", POLICY_VERSION);
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/v0/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID userId = userId(login);
        redis.delete(RefreshTokens.USER_KEY + userId);

        retention.purgeUnreachable(afterGrace());

        assertTrue(users.findActive(userId).isPresent(), "다시 로그인하면 되찾는 계정이다");
        assertFalse(withdrawalService.purgeGuest(userId), "잡이 아닌 호출이 소셜 계정을 넘겨도 지우지 않는다");
        assertTrue(users.findActive(userId).isPresent());
    }

    @Test
    void Refresh_저장소가_답하지_않으면_아무것도_지우지_않고_그만둔다() throws Exception {
        JsonNode guest = guest();
        redis.delete(RefreshTokens.USER_KEY + userId(guest));
        RefreshTokens down = new RefreshTokens(redis, properties) {
            @Override
            public boolean hasLiveLogin(UUID userId) {
                throw new ApiException(ErrorCode.AUTH_STORE_UNAVAILABLE);
            }
        };

        int purged = new GuestAccountRetention(users, down, withdrawalService).purgeUnreachable(afterGrace());

        assertEquals(0, purged);
        assertTrue(users.findActive(userId(guest)).isPresent(), "모른다를 없다로 읽지 않는다");
    }

    private static Instant afterGrace() {
        return Instant.now().plus(GuestAccountRetention.GRACE).plusSeconds(60);
    }

    private JsonNode guest() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String ip = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
        String body = mockMvc.perform(post("/v0/auth/guest")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("privacyConsent", true, "privacyPolicyVersion", POLICY_VERSION))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static UUID userId(JsonNode tokens) {
        return UUID.fromString(tokens.get("user").get("id").asString());
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asString();
    }
}
