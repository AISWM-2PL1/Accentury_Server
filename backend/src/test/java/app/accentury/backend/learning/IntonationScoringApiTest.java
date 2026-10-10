package app.accentury.backend.learning;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.analysis.AiAnalysisClient;
import app.accentury.backend.analysis.AnalysisDispatcher;
import app.accentury.backend.analysis.AnalysisJobRepository;
import app.accentury.backend.analysis.AnalysisJobStatus;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.upload.WavFixtures;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 억양 학습 채점 API의 실행 가능한 명세 (KAN-267, 명세서 §3.19) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * 전달 큐는 받아 적기만 하는 디스패처로 바꾸고, AI 결과는 워커가 부르는 것과 같은 {@link IntonationAttemptLedger}를
 * 테스트가 직접 불러 넣는다 - 워커의 재전송과 회로는 {@code HttpAnalysisDispatcherTest}가 본다. 카드는 발행본을
 * {@link IntonationLearningRegistry}에서 읽는다 (재발행돼도 테스트가 옛 카드를 붙들지 않게).
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class IntonationScoringApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;
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

    /** 전달된 요청을 받아 적기만 한다. */
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

    @Autowired
    private IntonationAttemptLedger ledger;

    @Autowired
    private IntonationAttemptRepository attempts;

    @Autowired
    private IntonationLearningRegistry registry;

    @Autowired
    private AnalysisJobRepository analysisJobs;

    @BeforeEach
    void clear() {
        dispatcher.requests.clear();
    }

    // === 인증과 경로 ===

    @Test
    void 토큰이_없으면_업로드도_조회도_401이다() throws Exception {
        upload(null, firstCardId(), "k").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/v0/learning/intonation-attempts/la_none")).andExpect(status().isUnauthorized());
        assertTrue(dispatcher.requests.isEmpty());
    }

    @Test
    void 없는_카드는_404이고_전달하지_않는다() throws Exception {
        String access = signUp();
        upload(access, "nope", "k").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_CARD_NOT_FOUND"));
        assertTrue(dispatcher.requests.isEmpty());
    }

    @Test
    void 멱등_키가_없으면_400이다() throws Exception {
        String access = signUp();
        upload(access, firstCardId(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertTrue(dispatcher.requests.isEmpty());
    }

    // === 업로드와 전달 ===

    @Test
    void 업로드는_202이고_카드의_scriptKey와_학습_점수_버전으로_전달된다() throws Exception {
        String access = signUp();
        IntonationLearningDefinition.Card card = firstCard();

        JsonNode accepted = body(upload(access, card.cardId(), "k1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.cardId").value(card.cardId()))
                .andExpect(jsonPath("$.pollAfterMs").isNumber()));

        String attemptId = accepted.get("attemptId").asString();
        assertTrue(attemptId.startsWith("la_"));
        assertEquals(1, dispatcher.requests.size());
        AnalysisDispatcher.AnalysisRequest request = dispatcher.requests.get(0);
        assertTrue(request.learning());
        assertEquals(attemptId, request.analysisJobId());
        assertEquals(card.cardId(), request.itemId());
        assertEquals(card.scriptKey(), request.scriptKey());
        assertEquals(registry.current().definition().contentVersion(), request.testVersion());
        assertEquals("sv-0.5", request.scoreVersion());
        assertEquals("GYEONGNAM", request.region());
        assertEquals(3000, request.durationMs());
        // 음성 저장에 동의하지 않은 계정은 동의 없이 넘어가고 라벨 전용 저장도 없다 (§3.19).
        assertNull(request.voiceConsent());
        assertFalse(request.labelOnlyWithoutConsent());

        poll(access, attemptId).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 같은_멱등_키로_다시_올려도_분석은_한_번만_돈다() throws Exception {
        String access = signUp();
        String cardId = firstCardId();

        String first = body(upload(access, cardId, "same").andExpect(status().isAccepted()))
                .get("attemptId").asString();
        String second = body(upload(access, cardId, "same").andExpect(status().isAccepted()))
                .get("attemptId").asString();

        assertEquals(first, second);
        assertEquals(1, dispatcher.requests.size());
        // 새 키는 새 시도다 (재녹음).
        upload(access, cardId, "other").andExpect(status().isAccepted());
        assertEquals(2, dispatcher.requests.size());
    }

    @Test
    void 음성_저장에_동의한_계정은_동의와_소유_계정이_실린다() throws Exception {
        String access = signUp();
        mockMvc.perform(put("/v0/users/me/voice-consent").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": \"" + CONSENT_VERSION + "\"}"))
                .andExpect(status().isOk());

        String attemptId = body(upload(access, firstCardId(), "k").andExpect(status().isAccepted()))
                .get("attemptId").asString();

        AnalysisDispatcher.AnalysisRequest request = dispatcher.requests.get(0);
        assertNotNull(request.voiceConsent());
        assertEquals(CONSENT_VERSION, request.voiceConsent().version());
        assertEquals(attempts.findById(attemptId).orElseThrow().userId(), request.voiceConsent().ownerId());
    }

    // === 결과 ===

    @Test
    void 완료_응답에_변환_점수와_올리고_내릴_음절이_있고_코멘트는_없다() throws Exception {
        String access = signUp();
        String attemptId = uploaded(access, firstCardId(), "k");

        // 원점수 95는 계수 95%로 90.25 -> 90. 올릴 음절이 있으니 100점 처리가 아니다.
        assertTrue(ledger.complete(attemptId, completed(95, List.of(
                new AiAnalysisClient.OrderSegment("끓일라고", "끓", "라", 0.8),
                new AiAnalysisClient.OrderSegment("가마솥에", null, null, 0.4),
                new AiAnalysisClient.OrderSegment("부우면", null, null, 0.9)))));

        JsonNode result = body(poll(access, attemptId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(90))
                .andExpect(jsonPath("$.scoreVersion").value("sv-0.5"))
                .andExpect(jsonPath("$.modelVersion").value("track1-test"))
                .andExpect(jsonPath("$.error").doesNotExist()));
        JsonNode feedback = result.get("pitchFeedback");
        assertEquals(3, feedback.size());
        assertEquals("끓일라고", feedback.get(0).get("word").asString());
        assertEquals("끓", feedback.get(0).get("raise").asString());
        assertEquals("라", feedback.get(0).get("lower").asString());
        assertFalse(feedback.get(0).get("split").asBoolean());
        // 경남 화자끼리 갈리는 어절은 두 음절이 null이고 split이 true다.
        assertTrue(feedback.get(1).get("raise").isNull());
        assertTrue(feedback.get(1).get("lower").isNull());
        assertTrue(feedback.get(1).get("split").asBoolean());
        // 가장 높인 음절은 같고 나머지 순서만 다른 어절은 두 음절이 null이고 split이 false다.
        assertTrue(feedback.get(2).get("raise").isNull());
        assertFalse(feedback.get(2).get("split").asBoolean());
        // 직전 시도가 없으면 키는 있고 값이 null이다.
        assertTrue(result.has("previousScore"));
        assertTrue(result.get("previousScore").isNull());
        // 원점수와 코멘트 문장은 응답에 없다.
        assertFalse(result.has("rawScore"));
        assertFalse(result.has("comment"));
        assertEquals(95, attempts.findById(attemptId).orElseThrow().rawScore());
    }

    @Test
    void 변환_점수가_90점_이상이고_올리고_내릴_음절이_없으면_100점이고_2회차에_직전_점수가_실린다() throws Exception {
        String access = signUp();
        String cardId = firstCardId();
        String first = uploaded(access, cardId, "k1");
        ledger.complete(first, completed(95, List.of(new AiAnalysisClient.OrderSegment("가마솥에", "가", "솥", 0.9))));

        // 원점수 96은 계수 100%라 96이고, 갈리는 어절만 있어 보여 줄 음절이 없으니 100점이다.
        String second = uploaded(access, cardId, "k2");
        ledger.complete(second, completed(96, List.of(new AiAnalysisClient.OrderSegment("가마솥에", null, null, 0.3))));

        poll(access, second).andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.previousScore").value(90));
        // 다른 카드의 시도는 직전 대비에 섞이지 않는다.
        String otherCard = uploaded(access, secondCardId(), "k3");
        ledger.complete(otherCard, completed(50, List.of()));
        poll(access, otherCard).andExpect(jsonPath("$.previousScore").isEmpty());
    }

    @Test
    void 분석_실패_오독_조용함은_서로_다른_상태로_구분된다() throws Exception {
        String access = signUp();
        String misread = uploaded(access, firstCardId(), "m");
        String quiet = uploaded(access, firstCardId(), "q");
        String broken = uploaded(access, firstCardId(), "b");
        ledger.fail(misread, AnalysisJobStatus.RETRYABLE_FAILED, "ANALYSIS_MISREAD");
        ledger.fail(quiet, AnalysisJobStatus.RETRYABLE_FAILED, "AUDIO_TOO_QUIET");
        ledger.fail(broken, AnalysisJobStatus.FAILED, "INTERNAL_ERROR");

        poll(access, misread).andExpect(jsonPath("$.status").value("RETRYABLE_FAILED"))
                .andExpect(jsonPath("$.error.code").value("ANALYSIS_MISREAD"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.previousScore").doesNotExist());
        poll(access, quiet).andExpect(jsonPath("$.error.code").value("AUDIO_TOO_QUIET"));
        poll(access, broken).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.retryable").value(false));
        // 종결된 시도에 늦게 온 성공은 버려진다.
        assertFalse(ledger.complete(misread, completed(80, List.of())));
    }

    @Test
    void 남의_시도는_403이고_없는_시도는_404다() throws Exception {
        String owner = signUp();
        String stranger = signUp();
        String attemptId = uploaded(owner, firstCardId(), "k");

        poll(stranger, attemptId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_FORBIDDEN"));
        poll(owner, "la_missing").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_NOT_FOUND"));
    }

    // === 혼잡 판정과 탈퇴 ===

    @Test
    void 처리_중인_학습_시도는_혼잡_판정의_처리_중_건수에_더해진다() throws Exception {
        String access = signUp();
        long before = analysisJobs.countProcessing();
        String attemptId = uploaded(access, firstCardId(), "k");

        assertEquals(before + 1, analysisJobs.countProcessing());
        ledger.complete(attemptId, completed(70, List.of()));
        assertEquals(before, analysisJobs.countProcessing());
    }

    @Test
    void 탈퇴하면_억양_학습_시도가_파기된다() throws Exception {
        String access = signUp();
        String other = signUp();
        String mine = uploaded(access, firstCardId(), "k");
        String theirs = uploaded(other, firstCardId(), "k");
        UUID userId = attempts.findById(mine).orElseThrow().userId();

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent());

        assertTrue(attempts.findById(mine).isEmpty());
        assertTrue(attempts.findById(theirs).isPresent());
        // 탈퇴 뒤에 늦게 온 결과는 0행으로 버려진다.
        assertFalse(ledger.complete(mine, completed(70, List.of())));
        assertEquals(0, attempts.findAll().stream().filter(a -> a.userId().equals(userId)).count());
    }

    // === 도우미 ===

    private static AiAnalysisClient.Completed completed(int rawScore, List<AiAnalysisClient.OrderSegment> segments) {
        return new AiAnalysisClient.Completed(rawScore, "OK", "track1-test", "sv-0.5", segments);
    }

    private IntonationLearningDefinition.Card firstCard() {
        return registry.current().definition().courses().get(0).cards().get(0);
    }

    private String firstCardId() {
        return firstCard().cardId();
    }

    private String secondCardId() {
        return registry.current().definition().courses().get(0).cards().get(1).cardId();
    }

    private String uploaded(String access, String cardId, String key) throws Exception {
        return body(upload(access, cardId, key).andExpect(status().isAccepted())).get("attemptId").asString();
    }

    private ResultActions upload(String access, String cardId, String key) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/v0/learning/intonation-cards/" + cardId + "/recordings")
                .file(new MockMultipartFile("audio", "recording.wav", "audio/wav", WavFixtures.standardWav(3000)))
                .file(new MockMultipartFile("meta", "", "application/json",
                        VALID_META.getBytes(StandardCharsets.UTF_8)));
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions poll(String access, String attemptId) throws Exception {
        return mockMvc.perform(get("/v0/learning/intonation-attempts/" + attemptId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
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

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
