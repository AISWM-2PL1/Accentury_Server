package app.accentury.backend.auth;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.session.TestSession;
import app.accentury.backend.session.TestSessionRepository;
import app.accentury.backend.training.TrainingVoiceOwners;
import app.accentury.backend.training.VoiceConsent;
import app.accentury.backend.training.VoiceConsents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 음성 저장 선택 동의 (KAN-269, 명세서 §3.1, §3.15) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * 본다: 앱은 계정에, 웹 익명은 세션에 동의가 남는다. 동의하지 않아도 세션은 만들어진다. 업로드가 쓰는 판정
 * ({@link VoiceConsents})이 익명 세션은 세션의 동의를, 계정 세션은 지금 계정의 동의를 본다. 대응표
 * ({@link TrainingVoiceOwners})는 세션마다 한 행이다. S3 쪽 규약은 {@code S3TrainingSampleStoreTest}가 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
@ExtendWith(OutputCaptureExtension.class)
class VoiceConsentApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;
    /** 게시 중인 음성 저장 동의 버전 - 설정 기본값을 그대로 쓴다. */
    private static final String CONSENT_VERSION = AccenturyProperties.Training.VOICE_CONSENT_VERSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TestSessionRepository sessions;

    @Autowired
    private VoiceConsents voiceConsents;

    @Autowired
    private TrainingVoiceOwners owners;

    @Autowired
    private JdbcTemplate jdbc;

    // === 앱 계정의 동의 (§3.15) ===

    @Test
    void 가입한_계정은_동의가_없고_게시_중인_버전을_안내받는다() throws Exception {
        String access = signUp();

        me(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceConsent.consented").value(false))
                .andExpect(jsonPath("$.voiceConsent.version").doesNotExist())
                .andExpect(jsonPath("$.voiceConsent.currentVersion").value(CONSENT_VERSION));
    }

    @Test
    void 동의하면_계정에_버전과_시각이_남고_철회하면_동의가_없어진다() throws Exception {
        String access = signUp();

        JsonNode consented = body(consent(access, CONSENT_VERSION).andExpect(status().isOk()));
        assertTrue(consented.get("voiceConsent").get("consented").asBoolean());
        assertEquals(CONSENT_VERSION, consented.get("voiceConsent").get("version").asString());
        AppUser user = users.findById(UUID.fromString(consented.get("user").get("id").asString())).orElseThrow();
        assertTrue(user.hasVoiceConsent());
        assertNotNull(user.voiceConsentAt());

        withdrawConsent(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceConsent.consented").value(false))
                .andExpect(jsonPath("$.voiceConsent.version").doesNotExist());
        AppUser withdrawn = users.findById(user.id()).orElseThrow();
        assertFalse(withdrawn.hasVoiceConsent());
        assertEquals(CONSENT_VERSION, withdrawn.voiceConsentVersion(), "철회해도 동의했던 버전은 이력으로 남는다");

        // 다시 동의하면 유효해진다.
        consent(access, CONSENT_VERSION).andExpect(jsonPath("$.voiceConsent.consented").value(true));
        assertTrue(users.findById(user.id()).orElseThrow().hasVoiceConsent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01", "latest", " " + CONSENT_VERSION, CONSENT_VERSION + "-rc"})
    void 게시_중인_버전과_다른_동의는_400이고_기록되지_않는다(String version) throws Exception {
        String access = signUp();

        consent(access, version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        me(access).andExpect(jsonPath("$.voiceConsent.consented").value(false));
    }

    @Test
    void 버전이_없는_동의는_400이고_동의한_적_없는_철회는_200이다() throws Exception {
        String access = signUp();

        mockMvc.perform(put("/v0/users/me/voice-consent").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isBadRequest());
        withdrawConsent(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceConsent.consented").value(false));
    }

    @Test
    void 동의와_철회는_Access_토큰이_필요하다() throws Exception {
        mockMvc.perform(put("/v0/users/me/voice-consent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": \"" + CONSENT_VERSION + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v0/users/me/voice-consent")).andExpect(status().isUnauthorized());
    }

    // === 웹 익명 세션의 동의 (§3.1) ===

    @Test
    void 동의_버전을_보낸_익명_세션은_세션에_동의가_남는다() throws Exception {
        TestSession session = createSession(null, CONSENT_VERSION);

        assertEquals(CONSENT_VERSION, session.voiceConsentVersion());
        assertNotNull(session.voiceConsentAt());
        VoiceConsent consent = voiceConsents.forSession(session);
        assertNotNull(consent);
        assertEquals(CONSENT_VERSION, consent.version());
        assertNull(consent.ownerId(), "익명 세션은 소유 계정이 없다");
    }

    @Test
    void 동의하지_않아도_세션은_만들어지고_동의는_없다() throws Exception {
        TestSession session = createSession(null, null);

        assertNull(session.voiceConsentVersion());
        assertNull(voiceConsents.forSession(session));
    }

    @Test
    void 게시_중인_버전과_다른_동의_버전은_400이고_세션이_생기지_않는다() throws Exception {
        long before = sessions.count();

        mockMvc.perform(post("/v0/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voiceConsentVersion\": \"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(before, sessions.count());
    }

    // === 계정 세션은 지금 계정의 동의를 본다 ===

    @Test
    void 계정_세션은_본문의_동의를_무시하고_계정의_동의를_본다() throws Exception {
        String access = signUp();

        // 본문에 동의 버전을 실어도 계정이 동의하지 않았으면 동의가 없다 - 틀린 값이어도 400이 아니다.
        TestSession session = createSession(access, "not-a-version");
        assertNull(session.voiceConsentVersion());
        assertNull(voiceConsents.forSession(session));

        consent(access, CONSENT_VERSION).andExpect(status().isOk());
        VoiceConsent consent = voiceConsents.forSession(session);
        assertNotNull(consent, "세션을 만든 뒤에 동의해도 다음 업로드부터 유효하다");
        assertEquals(session.userId(), consent.ownerId());

        withdrawConsent(access).andExpect(status().isOk());
        assertNull(voiceConsents.forSession(session), "세션 도중에 철회하면 다음 업로드부터 저장하지 않는다");
    }

    @Test
    void 탈퇴한_계정의_세션은_동의가_없다() throws Exception {
        String access = signUp();
        consent(access, CONSENT_VERSION).andExpect(status().isOk());
        TestSession session = createSession(access, null);
        assertNotNull(voiceConsents.forSession(session));

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent());

        // 탈퇴는 세션의 귀속을 끊는다 (KAN-241) - 끊기기 전에 읽은 세션으로 물어도 계정이 탈퇴 상태라 동의가 없다.
        assertNull(voiceConsents.forSession(session));
    }

    // === 계정과 음성의 대응표 ===

    @Test
    void 대응표는_세션마다_한_행이고_다시_기록해도_늘지_않는다() throws Exception {
        String access = signUp();
        TestSession session = createSession(access, null);
        UUID userId = session.userId();
        assertNotNull(userId);

        owners.record(session.id(), userId, Instant.parse("2026-10-04T01:00:00Z"));
        owners.record(session.id(), userId, Instant.parse("2026-10-04T02:00:00Z"));

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from training_voice_owner where session_id = ?", Integer.class, session.id()));
        assertEquals(userId, jdbc.queryForObject(
                "select user_id from training_voice_owner where session_id = ?", UUID.class, session.id()));

        // 세션 행이 지워져도 대응표는 남는다 - 만료 뒤에 계정의 음성을 찾는 것이 이 표의 목적이다.
        sessions.deleteById(session.id());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from training_voice_owner where user_id = ?", Integer.class, userId));
    }

    @Test
    void 동의_로그에_버전은_남고_세션_생성_로그에_동의_여부만_남는다(CapturedOutput output) throws Exception {
        String access = signUp();
        consent(access, CONSENT_VERSION).andExpect(status().isOk());
        TestSession session = createSession(null, CONSENT_VERSION);

        String logs = output.getOut();
        assertTrue(logs.contains("음성 저장 동의 userId="));
        String line = logs.lines().filter(l -> l.contains("세션 생성 sessionId=" + session.id())).findFirst().orElseThrow();
        assertTrue(line.contains("voiceConsent=true"));
    }

    private String signUp() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:sub-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", POLICY_VERSION);
        String access = body(mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())).get("accessToken").asString();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", "user@example.com");
        profile.put("name", "박유현");
        profile.put("birthDate", "1999-03-02");
        profile.put("gender", "MALE");
        profile.put("region", "GYEONGNAM");
        mockMvc.perform(put("/v0/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());
        return access;
    }

    private ResultActions me(String access) throws Exception {
        return mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
    }

    private ResultActions consent(String access, String version) throws Exception {
        return mockMvc.perform(put("/v0/users/me/voice-consent")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", version))));
    }

    private ResultActions withdrawConsent(String access) throws Exception {
        return mockMvc.perform(delete("/v0/users/me/voice-consent")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
    }

    private TestSession createSession(String access, String voiceConsentVersion) throws Exception {
        var request = post("/v0/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(voiceConsentVersion == null ? "{}"
                        : objectMapper.writeValueAsString(Map.of("voiceConsentVersion", voiceConsentVersion)));
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        String sessionId = body(mockMvc.perform(request).andExpect(status().isCreated()))
                .get("sessionId").asString();
        return sessions.findById(sessionId).orElseThrow();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
