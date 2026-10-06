package app.accentury.backend.result;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.SessionTestFlow;
import app.accentury.backend.SessionTestFlow.SessionHandle;
import app.accentury.backend.analysis.AnalysisJob;
import app.accentury.backend.analysis.AnalysisJobRepository;
import app.accentury.backend.analysis.AnalysisJobStatus;
import app.accentury.backend.analysis.AnalysisJobTransitions;
import app.accentury.backend.testdefinition.ActiveVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 7문항 개편의 실행 가능한 명세 (KAN-260 AC - 새 정의는 7문항, 배포 전 10문항 세션은 그대로).
 * <p>
 * {@code gn-2026.10.1}(V5)은 {@code gn-2026.09.4}의 문항 본문 그대로 세트 구성만 VVWVWWW로 바꾸고
 * sv-0.5를 선언한 재발행이다. 전환 뒤 새 세션은 음성 3 + 어휘 4로 응시하고 집계되며, 전환 전에
 * 만든 세션은 생성 시점에 고정한 정의대로 10문항으로 완료되어야 한다 (§5.4).
 */
@AutoConfigureMockMvc
class SevenItemSetRolloutTest extends IntegrationTest {

    /** 마이그레이션이 최초 활성으로 지정한 버전 (테스트 픽스처 기준). */
    private static final String BASELINE = "gn-2026.08.1";

    /** 10문항 정의 - setLayout이 없다. */
    private static final String TEN_ITEMS = "gn-2026.09.4";

    /** 7문항 정의 (V5, sv-0.5) */
    private static final String SEVEN_ITEMS = "gn-2026.10.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private AnalysisJobTransitions transitions;

    @Autowired
    private ActiveVersionService activeVersions;

    private SessionTestFlow flow;

    @BeforeEach
    void setUpFlow() {
        flow = new SessionTestFlow(mockMvc, objectMapper, analysisJobRepository, transitions);
    }

    @AfterEach
    void restoreBaseline() {
        activeVersions.activate(BASELINE, "테스트 정리", "127.0.0.1");
    }

    @Test
    void 일곱_문항_정의의_세트는_v_v_w_v_w_w_w_순서다() throws Exception {
        mockMvc.perform(get("/v0/tests/" + SEVEN_ITEMS).param("voiceSet", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreVersion").value("sv-0.5"))
                .andExpect(jsonPath("$.estimatedDurationSec").value(180))
                .andExpect(jsonPath("$.voiceSetCount").value(49))
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[*].itemId").value(org.hamcrest.Matchers.contains(
                        "v1", "v2", "w1", "v3", "w2", "w3", "w4")))
                .andExpect(jsonPath("$.items[*].type").value(org.hamcrest.Matchers.contains(
                        "VOICE", "VOICE", "VOCABULARY", "VOICE", "VOCABULARY", "VOCABULARY", "VOCABULARY")))
                .andExpect(jsonPath("$.items[*].seq").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.setLayout").doesNotExist());
        // 마지막 세트는 음성이 처음으로 돌아가고(145, 1, 2) 어휘는 세트 37부터 순환해 48..51이다.
        mockMvc.perform(get("/v0/tests/" + SEVEN_ITEMS).param("voiceSet", "49"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].itemId").value(org.hamcrest.Matchers.contains(
                        "v145", "v1", "w48", "v2", "w49", "w50", "w51")));
        // 10문항 정의는 그대로 10문항 교차다.
        mockMvc.perform(get("/v0/tests/" + TEN_ITEMS).param("voiceSet", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.voiceSetCount").value(29));
    }

    @Test
    void 전환_뒤_새_세션은_7문항_sv05로_전환_전_세션은_10문항으로_완료된다() throws Exception {
        activeVersions.activate(TEN_ITEMS, "KAN-260 전환 전 상태", "127.0.0.1");
        SessionHandle before = createSession(TEN_ITEMS, "sv-0.4");

        activeVersions.activate(SEVEN_ITEMS, "KAN-260 7문항 전환", "127.0.0.1");
        SessionHandle after = createSession(SEVEN_ITEMS, "sv-0.5");

        // 7문항 세션의 세트 1에 w5와 v4는 없다 - 같은 풀에 있어도 422다.
        mockMvc.perform(post("/v0/sessions/" + after.id() + "/vocab-items/w5/answer")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"choiceId\": \"w5c\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + after.token())
                        .header("Idempotency-Key", "vocab-w5-outside"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_IN_VERSION"));

        // 세트 1의 어휘 정답은 w1b, w2c, w3a, w4a, w5c다 - 두 정의의 어휘 문항이 바이트 단위로 같다
        // (V5 머리말). 아래는 앞 셋만 정답이다.
        // 10문항 세션: 원점수 95 x 5, 정답 3/5 → 억양 90, 단어 60, 종합 80 (sv-0.4).
        flow.answerVocab(before, Map.of("w1", "w1b", "w2", "w2c", "w3", "w3a", "w4", "w4b", "w5", "w5a"));
        completeVoice(before, List.of("v1", "v2", "v3", "v4", "v5"), "sv-0.4", 95);
        // 7문항 세션: 원점수 95 x 3, 정답 3/4 → 억양 95 x 0.95 = 90.25 → 90, 단어 75,
        // 종합 (90 x 2 + 75) / 3 = 85 (sv-0.5).
        flow.answerVocab(after, Map.of("w1", "w1b", "w2", "w2c", "w3", "w3a", "w4", "w4b"));
        completeVoice(after, List.of("v1", "v2", "v3"), "sv-0.5", 95);

        // 진행도의 분모도 세트 문항 수다.
        mockMvc.perform(post("/v0/sessions/" + after.id() + "/vocab-items/w1/answer")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"choiceId\": \"w1b\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + after.token())
                        .header("Idempotency-Key", "vocab-w1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(7));

        flow.complete(before, "before");
        flow.complete(after, "after");

        mockMvc.perform(result(before))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testVersion").value(TEN_ITEMS))
                .andExpect(jsonPath("$.scoreVersion").value("sv-0.4"))
                .andExpect(jsonPath("$.scores.intonation").value(90))
                .andExpect(jsonPath("$.scores.vocabulary").value(60))
                .andExpect(jsonPath("$.scores.overall").value(80));
        mockMvc.perform(result(after))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testVersion").value(SEVEN_ITEMS))
                .andExpect(jsonPath("$.scoreVersion").value("sv-0.5"))
                .andExpect(jsonPath("$.scores.intonation").value(90))
                .andExpect(jsonPath("$.scores.vocabulary").value(75))
                .andExpect(jsonPath("$.scores.overall").value(85))
                .andExpect(jsonPath("$.tier.code").value("NATIVE"));
    }

    @Test
    void 일곱_문항_세션은_음성_3_어휘_4가_다_있어야_완료된다() throws Exception {
        activeVersions.activate(SEVEN_ITEMS, "KAN-260 7문항 전환", "127.0.0.1");
        SessionHandle session = createSession(SEVEN_ITEMS, "sv-0.5");
        flow.answerVocab(session, Map.of("w1", "w1b", "w2", "w2c", "w3", "w3a"));
        completeVoice(session, List.of("v1", "v2", "v3"), "sv-0.5", 80);

        mockMvc.perform(post(SessionTestFlow.completeUrl(session))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token())
                        .header("Idempotency-Key", "incomplete"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESULT_INCOMPLETE"))
                .andExpect(jsonPath("$.missingItems[*]").value(org.hamcrest.Matchers.contains("w4")));
    }

    private SessionHandle createSession(String testVersion, String scoreVersion) throws Exception {
        // 세트 1을 못 박는 것은 정답표를 상수로 두기 위해서다 (KAN-205).
        MvcResult created = mockMvc.perform(post("/v0/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"voiceSet\": 1 }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.testVersion").value(testVersion))
                .andExpect(jsonPath("$.scoreVersion").value(scoreVersion))
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        return new SessionHandle(json.get("sessionId").asString(), json.get("sessionToken").asString());
    }

    /** 음성 문항마다 시도 1건을 심고 같은 원점수로 종결한다. AI 회신의 scoreVersion도 세션 버전에 맞춘다. */
    private void completeVoice(SessionHandle session, List<String> itemIds, String scoreVersion, int rawScore) {
        Instant base = Instant.now();
        for (String itemId : itemIds) {
            AnalysisJob job = analysisJobRepository.save(new AnalysisJob(
                    "a_" + UUID.randomUUID(), session.id(), itemId,
                    1, "k-" + UUID.randomUUID(), AnalysisJobStatus.PROCESSING, base));
            transitions.complete(job.id(), rawScore, "OK", "stub-0.1", scoreVersion);
        }
    }

    private static org.springframework.test.web.servlet.RequestBuilder result(SessionHandle session) {
        return get("/v0/sessions/" + session.id() + "/result")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
    }
}
