package app.accentury.backend.learning;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 단어 학습 API의 실행 가능한 명세 (KAN-265, 명세서 §3.16) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * 정답표는 발행본 {@code wd-gn-2026.10.1}(V8)을 {@link WordLearningRegistry}에서 읽는다 - 리터럴로 박지 않는 것은 발행본이
 * 재발행되면 테스트가 조용히 옛 정답을 검사하게 되기 때문이다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class WordLearningApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WordLearningRegistry registry;

    @Autowired
    private WordSetAttemptRepository attempts;

    @Autowired
    private WordAttemptAnswerRepository answers;

    @Autowired
    private WordWrongAnswerRepository wrongAnswers;

    @Autowired
    private JdbcTemplate jdbc;

    // === 인증 (§2.1, AC 1) ===

    @Test
    void 토큰이_없으면_전_경로가_401이다() throws Exception {
        String setId = firstSet().setId();
        mockMvc.perform(get("/v0/learning/word-sets")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/v0/learning/word-sets/" + setId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v0/learning/word-sets/" + setId + "/attempts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v0/learning/word-attempts/wa_x/items/ws01q01/answer")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "k").content("{\"choiceId\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v0/learning/word-attempts/wa_x/complete")).andExpect(status().isUnauthorized());
    }

    // === 조회 (AC 1, AC 2) ===

    @Test
    void 로그인한_계정은_세트_목록과_상세를_받고_상세에는_정답이_없다() throws Exception {
        String access = signUp();
        WordLearningRegistry.Published current = registry.current();

        JsonNode list = body(authed(get("/v0/learning/word-sets"), access)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        assertEquals(current.definition().contentVersion(), list.get("contentVersion").asString());
        assertEquals(current.sets().size(), list.get("sets").size());
        JsonNode first = list.get("sets").get(0);
        WordLearningDefinition.Set firstSet = firstSet();
        assertEquals(firstSet.setId(), first.get("setId").asString());
        assertEquals(1, first.get("seq").asInt());
        assertEquals(firstSet.level(), first.get("level").asInt());
        assertEquals(firstSet.cards().size(), first.get("cardCount").asInt());
        assertEquals(firstSet.items().size(), first.get("itemCount").asInt());

        JsonNode detail = body(authed(get("/v0/learning/word-sets/" + firstSet.setId()), access)
                .andExpect(status().isOk()));
        assertEquals(firstSet.cards().size(), detail.get("cards").size());
        assertEquals(firstSet.items().size(), detail.get("items").size());
        JsonNode item = detail.get("items").get(0);
        assertEquals(4, item.get("choices").size());
        assertFalse(item.has("correctChoiceId"), "조회 응답에 정답이 실리면 안 된다");
        assertFalse(item.has("explanation"), "조회 응답에 해설이 실리면 안 된다");
        assertFalse(detail.toString().contains(firstSet.items().get(0).explanation()));

        authed(get("/v0/learning/word-sets/ws99"), access)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_SET_NOT_FOUND"));
    }

    // === 제출과 채점, 세트 완료 (AC 2, AC 4) ===

    @Test
    void 목록부터_세트_완료까지_돌고_정답률과_오답_목록이_제출_기록과_일치한다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());

        // 첫 문항은 일부러 틀리고 나머지는 맞힌다.
        List<WordLearningDefinition.Item> items = set.items();
        WordLearningDefinition.Item wrongItem = items.get(0);
        String wrongChoice = wrongItem.choices().stream()
                .map(WordLearningDefinition.Choice::choiceId)
                .filter(id -> !id.equals(wrongItem.correctChoiceId()))
                .findFirst().orElseThrow();
        JsonNode wrong = body(answer(access, attemptId, wrongItem.itemId(), "k-" + wrongItem.itemId(), wrongChoice)
                .andExpect(status().isOk()));
        assertFalse(wrong.get("correct").asBoolean());
        assertEquals(wrongItem.correctChoiceId(), wrong.get("correctChoiceId").asString());
        assertEquals(wrongItem.correctText(), wrong.get("correctText").asString());
        assertEquals(wrongItem.explanation(), wrong.get("explanation").asString());
        assertEquals(1, wrong.get("answeredCount").asInt());
        assertEquals(items.size(), wrong.get("itemCount").asInt());

        for (WordLearningDefinition.Item item : items.subList(1, items.size())) {
            body(answer(access, attemptId, item.itemId(), "k-" + item.itemId(), item.correctChoiceId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true)));
        }

        JsonNode result = body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isOk()));
        int itemCount = items.size();
        assertEquals(itemCount, result.get("itemCount").asInt());
        assertEquals(itemCount - 1, result.get("correctCount").asInt());
        assertEquals((int) Math.round((itemCount - 1) * 100.0 / itemCount), result.get("accuracyPercent").asInt());
        assertEquals(1, result.get("wrongItems").size());
        JsonNode wrongEntry = result.get("wrongItems").get(0);
        assertEquals(wrongItem.itemId(), wrongEntry.get("itemId").asString());
        assertEquals(wrongChoice, wrongEntry.get("chosenChoiceId").asString());
        assertEquals(wrongItem.choice(wrongChoice).orElseThrow().text(), wrongEntry.get("chosenText").asString());
        assertEquals(wrongItem.correctChoiceId(), wrongEntry.get("correctChoiceId").asString());
        assertEquals(wrongItem.explanation(), wrongEntry.get("explanation").asString());
        assertTrue(result.hasNonNull("completedAt"));

        // 저장된 기록과 일치한다.
        WordSetAttempt stored = attempts.findById(attemptId).orElseThrow();
        assertTrue(stored.isCompleted());
        assertEquals(itemCount - 1, stored.correctCount());
        assertEquals(itemCount, answers.countByAttemptId(attemptId));

        // 완료 뒤 제출은 409이고, 완료를 다시 부르면 같은 본문이다 (멱등).
        answer(access, attemptId, wrongItem.itemId(), "k-" + wrongItem.itemId(), wrongChoice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_COMPLETED"));
        JsonNode again = body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isOk()));
        assertEquals(result, again);
    }

    @Test
    void 답안이_빠진_세트_완료는_422와_빠진_문항_목록이다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item first = set.items().get(0);
        answer(access, attemptId, first.itemId(), "k1", first.correctChoiceId()).andExpect(status().isOk());

        JsonNode error = body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_INCOMPLETE")));
        assertEquals(set.items().size() - 1, error.get("missingItems").size());
        assertFalse(error.get("missingItems").toString().contains(first.itemId()));
        assertFalse(attempts.findById(attemptId).orElseThrow().isCompleted());
    }

    // === 멱등 (AC 3) ===

    @Test
    void 같은_키로_다시_제출해도_기록이_1건이고_응답이_같다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item item = set.items().get(0);
        String wrongChoice = item.choices().stream().map(WordLearningDefinition.Choice::choiceId)
                .filter(id -> !id.equals(item.correctChoiceId())).findFirst().orElseThrow();

        JsonNode first = body(answer(access, attemptId, item.itemId(), "same-key", wrongChoice).andExpect(status().isOk()));
        JsonNode replay = body(answer(access, attemptId, item.itemId(), "same-key", wrongChoice).andExpect(status().isOk()));
        assertEquals(first, replay);
        assertEquals(1, answers.countByAttemptId(attemptId));
        assertEquals(1, wrongAnswers.findByUserIdAndContentVersionAndItemId(userIdOf(attemptId),
                set(attemptId).contentVersion(), item.itemId()).orElseThrow().wrongCount(), "재전송은 오답을 세지 않는다");

        // 같은 키로 다른 답은 400, 다른 키로 다시 내면 409다 - 답을 고치는 경로는 없다.
        answer(access, attemptId, item.itemId(), "same-key", item.correctChoiceId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        answer(access, attemptId, item.itemId(), "other-key", item.correctChoiceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_ALREADY_ANSWERED"));
        assertEquals(1, answers.countByAttemptId(attemptId));
        assertFalse(answers.findByAttemptIdAndItemId(attemptId, item.itemId()).orElseThrow().correct());
    }

    // === 검증 오류 ===

    @Test
    void 잘못된_제출은_정해진_코드로_거절된다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        WordLearningDefinition.Set other = registry.current().definition().sets().get(1);
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item item = set.items().get(0);

        // 없는 세트의 시도 시작은 404다.
        authed(post("/v0/learning/word-sets/ws99/attempts"), access)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_SET_NOT_FOUND"));
        // 없는 시도는 404다.
        answer(access, "wa_missing", item.itemId(), "k", item.correctChoiceId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_NOT_FOUND"));
        authed(post("/v0/learning/word-attempts/wa_missing/complete"), access).andExpect(status().isNotFound());
        // Idempotency-Key 누락과 choiceId 누락은 400이다.
        authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + item.itemId() + "/answer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"choiceId\":\"" + item.correctChoiceId() + "\"}"), access)
                .andExpect(status().isBadRequest());
        authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + item.itemId() + "/answer")
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "k").content("{}"), access)
                .andExpect(status().isBadRequest());
        // 이 세트의 문항이 아니거나 이 문항의 보기가 아니면 422다.
        WordLearningDefinition.Item foreign = other.items().get(0);
        answer(access, attemptId, foreign.itemId(), "k", foreign.correctChoiceId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_IN_VERSION"));
        answer(access, attemptId, item.itemId(), "k", set.items().get(1).correctChoiceId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_IN_VERSION"));
        assertEquals(0, answers.countByAttemptId(attemptId));

        // 남의 시도는 403이다 (FR-AC-11).
        String stranger = signUp();
        answer(stranger, attemptId, item.itemId(), "k", item.correctChoiceId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_FORBIDDEN"));
        authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), stranger)
                .andExpect(status().isForbidden());
    }

    // === 오답 저장과 탈퇴 (AC 5) ===

    @Test
    void 오답은_계정_기준으로_쌓이고_탈퇴하면_학습_기록이_전부_파기된다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        WordLearningDefinition.Item item = set.items().get(0);
        String wrongChoice = item.choices().stream().map(WordLearningDefinition.Choice::choiceId)
                .filter(id -> !id.equals(item.correctChoiceId())).findFirst().orElseThrow();

        // 두 시도에서 같은 문항을 틀리면 한 행에 2회로 합산된다.
        String first = startAttempt(access, set.setId());
        String second = startAttempt(access, set.setId());
        answer(access, first, item.itemId(), "k1", wrongChoice).andExpect(status().isOk());
        answer(access, second, item.itemId(), "k2", wrongChoice).andExpect(status().isOk());
        UUID userId = userIdOf(first);
        List<WordWrongAnswer> wrong = wrongAnswers.findByUserId(userId);
        assertEquals(1, wrong.size());
        assertEquals(2, wrong.get(0).wrongCount());
        assertEquals(set.setId(), wrong.get(0).setId());
        assertEquals(item.itemId(), wrong.get(0).itemId());
        assertNotEquals(wrong.get(0).firstWrongAt(), wrong.get(0).lastWrongAt());
        assertEquals(2, attempts.findByUserId(userId).size());

        // 다른 계정의 기록은 섞이지 않는다.
        String other = signUp();
        String otherAttempt = startAttempt(other, set.setId());
        answer(other, otherAttempt, item.itemId(), "k3", wrongChoice).andExpect(status().isOk());
        assertEquals(1, wrongAnswers.findByUserId(userIdOf(otherAttempt)).size());

        // 탈퇴하면 시도, 답안, 오답이 전부 사라지고 다른 계정의 것은 남는다.
        authed(post("/v0/users/me/withdrawal"), access).andExpect(status().isNoContent());
        assertTrue(attempts.findByUserId(userId).isEmpty());
        assertTrue(wrongAnswers.findByUserId(userId).isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from word_attempt_answer where attempt_id in (?, ?)", Integer.class, first, second));
        assertEquals(1, attempts.findByUserId(userIdOf(otherAttempt)).size());
        assertEquals(1, wrongAnswers.findByUserId(userIdOf(otherAttempt)).size());
        // 탈퇴한 토큰으로는 더 이상 학습에 들어갈 수 없다.
        authed(get("/v0/learning/word-sets"), access).andExpect(status().isUnauthorized());
    }

    // === 도우미 ===

    private WordLearningDefinition.Set firstSet() {
        return registry.current().definition().sets().get(0);
    }

    private WordSetAttempt set(String attemptId) {
        return attempts.findById(attemptId).orElseThrow();
    }

    private UUID userIdOf(String attemptId) {
        return set(attemptId).userId();
    }

    private String startAttempt(String access, String setId) throws Exception {
        JsonNode created = body(authed(post("/v0/learning/word-sets/" + setId + "/attempts"), access)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setId").value(setId))
                .andExpect(jsonPath("$.contentVersion").value(registry.current().definition().contentVersion())));
        assertEquals(registry.current().set(setId).items().size(), created.get("itemCount").asInt());
        return created.get("attemptId").asString();
    }

    private ResultActions answer(String access, String attemptId, String itemId, String key, String choiceId)
            throws Exception {
        return authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + itemId + "/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content("{\"choiceId\":\"" + choiceId + "\"}"), access);
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String access) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
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
