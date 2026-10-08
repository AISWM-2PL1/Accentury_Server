package app.accentury.backend.vocab;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.common.AdminAuth;
import app.accentury.backend.testdefinition.ActiveVersionService;
import app.accentury.backend.training.VocabAnswerSample;
import app.accentury.backend.training.VocabAnswerSampleStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 단어 답안의 정오 기록 (KAN-276) - 답안 API 경계에서 어떤 답안이 기록으로 넘어가는지 본다.
 * <p>
 * 기록 저장소 자체(키, 본문, 실패 삼킴)는 {@code S3VocabAnswerSampleStoreTest}가 본다. 정답표는 발행본
 * gn-2026.10.2(V11)의 세트 1이다 - w1 정답 w1c, w2 정답 w2d.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "accentury.admin.token=" + VocabAnswerSampleApiTest.ADMIN_TOKEN)
class VocabAnswerSampleApiTest extends IntegrationTest {

    static final String ADMIN_TOKEN = "vocab-answer-sample-test-admin-token-0123";

    /** 마이그레이션이 최초 활성으로 지정한 버전 (테스트 픽스처 기준). */
    private static final String BASELINE = "gn-2026.08.1";

    private static final String CURATED = "gn-2026.10.2";

    @TestConfiguration
    static class RecordingSamplesConfig {

        @Bean
        RecordingSamples recordingSamples() {
            return new RecordingSamples();
        }
    }

    /** 넘어온 기록을 받아 적기만 한다 - 버킷이 없는 테스트 배포에는 다른 저장소 빈이 없다. */
    static class RecordingSamples implements VocabAnswerSampleStore {

        final CopyOnWriteArrayList<VocabAnswerSample> samples = new CopyOnWriteArrayList<>();

        @Override
        public void save(VocabAnswerSample sample) {
            samples.add(sample);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActiveVersionService activeVersions;

    @Autowired
    private RecordingSamples recorded;

    @BeforeEach
    void clear() {
        recorded.samples.clear();
    }

    @AfterEach
    void restoreBaseline() {
        activeVersions.activate(BASELINE, "테스트 정리", "127.0.0.1");
    }

    @Test
    void 새_버전_세션의_단어_답안은_맞음과_틀림이_함께_기록된다() throws Exception {
        activeVersions.activate(CURATED, "KAN-276 검수 버전", "127.0.0.1");
        JsonNode session = createSession(null, "{\"voiceSet\": 1, \"region\": \"GYEONGNAM\"}");

        mockMvc.perform(answer(session, "w1", "k-1", "w1c")).andExpect(status().isOk());
        mockMvc.perform(answer(session, "w2", "k-2", "w2a")).andExpect(status().isOk());

        assertEquals(2, recorded.samples.size());
        VocabAnswerSample right = recorded.samples.get(0);
        assertEquals(session.get("sessionId").asString(), right.sessionId());
        assertEquals("w1", right.itemId());
        assertEquals("GYEONGNAM", right.region());
        assertEquals(CURATED, right.testVersion());
        assertEquals("sv-0.5", right.scoreVersion());
        assertEquals("w1c", right.choiceId());
        assertEquals("w1c", right.correctChoiceId());
        assertTrue(right.correct());
        assertTrue(right.answerId().startsWith("va_"));
        VocabAnswerSample wrong = recorded.samples.get(1);
        assertEquals("w2a", wrong.choiceId());
        assertEquals("w2d", wrong.correctChoiceId());
        assertFalse(wrong.correct());
    }

    @Test
    void 같은_키의_재전송은_다시_기록하지_않는다() throws Exception {
        activeVersions.activate(CURATED, "KAN-276 검수 버전", "127.0.0.1");
        JsonNode session = createSession(null, "{\"voiceSet\": 1}");

        mockMvc.perform(answer(session, "w1", "k-1", "w1c")).andExpect(status().isOk());
        mockMvc.perform(answer(session, "w1", "k-1", "w1c")).andExpect(status().isOk());

        assertEquals(1, recorded.samples.size());
        assertEquals("UNKNOWN", recorded.samples.get(0).region());
    }

    @Test
    void 기록_시작_전_버전의_세션은_기록하지_않는다() throws Exception {
        JsonNode session = createSession(null, "{}");

        mockMvc.perform(answer(session, "w1", "k-1", "w1a")).andExpect(status().isOk());

        assertTrue(recorded.samples.isEmpty());
    }

    @Test
    void 합성_트래픽_세션은_기록하지_않는다() throws Exception {
        // 배포 스모크는 관리자 토큰으로 세션을 만든다 (KAN-138) - 실제 응시가 아니라 난이도 집계를 흐린다.
        activeVersions.activate(CURATED, "KAN-276 검수 버전", "127.0.0.1");
        JsonNode session = createSession(ADMIN_TOKEN, "{\"voiceSet\": 1}");

        mockMvc.perform(answer(session, "w1", "k-1", "w1c")).andExpect(status().isOk());

        assertTrue(recorded.samples.isEmpty());
    }

    private JsonNode createSession(String adminToken, String body) throws Exception {
        var request = post("/v0/sessions").contentType(MediaType.APPLICATION_JSON).content(body);
        if (adminToken != null) {
            request.header(AdminAuth.TOKEN_HEADER, adminToken);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static RequestBuilder answer(JsonNode session, String itemId, String idempotencyKey, String choiceId) {
        return post("/v0/sessions/" + session.get("sessionId").asString() + "/vocab-items/" + itemId + "/answer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"choiceId\": \"" + choiceId + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("sessionToken").asString())
                .header("Idempotency-Key", idempotencyKey);
    }
}
