package app.accentury.backend.upload;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.analysis.AnalysisDispatcher;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.AdminAuth;
import app.accentury.backend.training.VoiceConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드가 분석으로 넘기는 요청에 실리는 음성 저장 동의 (KAN-269) - 업로드 경계에서 본다.
 * <p>
 * 학습 샘플은 이 값이 있는 요청만 된다({@code HttpAnalysisDispatcher}). 그래서 여기서 틀리면 동의하지 않은
 * 사람의 음성이 저장된다. 동의 판정 자체는 {@code VoiceConsentApiTest}가, 요청 이후는
 * {@code HttpAnalysisDispatcherTest}와 {@code S3TrainingSampleStoreTest}가 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = {"accentury.auth.fake-idp=true",
        "accentury.admin.token=" + VoiceUploadConsentTest.ADMIN_TOKEN})
class VoiceUploadConsentTest extends IntegrationTest {

    /** 합성 트래픽 표시를 검증할 관리자 토큰 - 배포 스모크가 쓰는 헤더다 (KAN-138). */
    static final String ADMIN_TOKEN = "voice-upload-consent-test-admin-token-0123";

    private static final String CONSENT_VERSION = AccenturyProperties.Training.VOICE_CONSENT_VERSION;

    private static final String VALID_META = """
            {"durationMs": 3000,
             "clientQuality": {"rms": 0.11, "peak": 0.83, "silenceRatio": 0.12, "clipped": false}}""";

    @TestConfiguration
    static class RecordingDispatcherConfig {

        @Bean
        @Primary
        RecordingDispatcher recordingDispatcher() {
            return new RecordingDispatcher();
        }
    }

    /** 분석으로 넘어온 요청을 받아 적기만 한다. */
    static class RecordingDispatcher implements AnalysisDispatcher {

        final CopyOnWriteArrayList<AnalysisRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public void dispatch(AnalysisRequest request) {
            requests.add(request);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingDispatcher dispatcher;

    @BeforeEach
    void clear() {
        dispatcher.requests.clear();
    }

    @Test
    void 동의하지_않은_익명_세션의_업로드는_동의_없이_넘어간다() throws Exception {
        JsonNode session = createSession(null, "{}");

        upload(session, "v1", "anon-none");

        assertNull(lastConsent());
        // 라벨 전용 저장 대상이라는 사실이 함께 넘어간다 (KAN-274) - 동의가 없어도 라벨(점수와 출신 지역)은 남긴다.
        assertTrue(lastRequest().labelOnlyWithoutConsent());
    }

    @Test
    void 합성_트래픽의_익명_세션은_동의가_없어도_라벨_전용_저장_대상이_아니다() throws Exception {
        // 배포 스모크는 관리자 토큰으로 익명 세션을 만든다 (KAN-138). 실제 응시가 아니라서 라벨을 남기지 않는다 -
        // 만료 없는 버킷에 배포마다 쌓이면 실사용자 건과 가려낼 표식이 없다 (검증 리뷰 P1).
        JsonNode session = objectMapper.readTree(mockMvc.perform(post("/v0/sessions")
                        .header(AdminAuth.TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        upload(session, "v1", "synthetic-none");

        assertNull(lastConsent());
        assertFalse(lastRequest().labelOnlyWithoutConsent());
    }

    @Test
    void 동의한_익명_세션의_업로드는_세션의_동의를_싣고_소유_계정은_없다() throws Exception {
        JsonNode session = createSession(null, "{\"voiceConsentVersion\": \"" + CONSENT_VERSION + "\"}");

        upload(session, "v1", "anon-consent");

        VoiceConsent consent = lastConsent();
        assertNotNull(consent);
        assertEquals(CONSENT_VERSION, consent.version());
        assertNull(consent.ownerId());
    }

    @Test
    void 동의하지_않은_계정_세션은_본문에_동의_버전을_실어도_동의_없이_라벨_전용으로_넘어간다() throws Exception {
        String access = signUp();
        JsonNode session = createSession(access, "{\"voiceConsentVersion\": \"" + CONSENT_VERSION + "\"}");

        upload(session, "v1", "account-none");

        assertNull(lastConsent());
        // KAN-276부터 계정 세션도 익명 세션과 같이 음성 없이 라벨 JSON만 남긴다 (KAN-274는 익명만이었다).
        assertTrue(lastRequest().labelOnlyWithoutConsent());
    }

    @Test
    void 계정이_동의하면_소유_계정이_실리고_세션_도중에_철회하면_다음_업로드부터_빠진다() throws Exception {
        String access = signUp();
        JsonNode me = consent(access);
        JsonNode session = createSession(access, "{}");

        upload(session, "v1", "account-consent-1");
        VoiceConsent consent = lastConsent();
        assertNotNull(consent);
        assertEquals(UUID.fromString(me.get("user").get("id").asString()), consent.ownerId());

        mockMvc.perform(delete("/v0/users/me/voice-consent").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
        upload(session, "v2", "account-consent-2");

        assertNull(lastConsent(), "철회한 뒤의 업로드에 동의가 실렸다");
        assertEquals(2, dispatcher.requests.size());
    }

    private VoiceConsent lastConsent() {
        return lastRequest().voiceConsent();
    }

    private AnalysisDispatcher.AnalysisRequest lastRequest() {
        return dispatcher.requests.get(dispatcher.requests.size() - 1);
    }

    private void upload(JsonNode session, String itemId, String idempotencyKey) throws Exception {
        mockMvc.perform(multipart("/v0/sessions/" + session.get("sessionId").asString()
                        + "/voice-items/" + itemId + "/recording")
                        .file(new MockMultipartFile("audio", "recording.wav", "audio/wav",
                                WavFixtures.standardWav(3000)))
                        .file(new MockMultipartFile("meta", "", "application/json",
                                VALID_META.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("sessionToken").asString())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isAccepted());
    }

    private JsonNode createSession(String access, String body) throws Exception {
        var request = post("/v0/sessions").contentType(MediaType.APPLICATION_JSON).content(body);
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode consent(String access) throws Exception {
        return objectMapper.readTree(mockMvc.perform(put("/v0/users/me/voice-consent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": \"" + CONSENT_VERSION + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String signUp() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:sub-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", AccenturyProperties.Auth.PRIVACY_POLICY_VERSION);
        String access = objectMapper.readTree(mockMvc.perform(post("/v0/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asString();
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
}
