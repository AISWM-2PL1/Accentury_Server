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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 정답표는 발행본 {@code wd-gn-2026.11.1}(V21, KAN-345)을 {@link WordLearningRegistry}에서 읽는다 - 리터럴로 박지 않는 것은 발행본이
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

    // === 발행본 구성 (KAN-345) ===

    @Test
    void 발행본은_구간_다섯에_세트_넷씩_문항_세_개씩_싣는다() {
        WordLearningDefinition current = registry.current().definition();
        assertEquals("wd-gn-2026.11.1", current.contentVersion());
        // 앱 학습 탭의 구간이 level이다. 구간마다 세트 4개, 세트마다 문항 3개이고 seq 순서가 쉬운 순이다.
        assertEquals(20, current.sets().size());
        Set<String> words = new HashSet<>();
        for (WordLearningDefinition.Set set : current.sets()) {
            assertEquals((set.seq() - 1) / 4 + 1, set.level(), set.setId());
            assertEquals(3, set.items().size(), set.setId());
            for (WordLearningDefinition.Card card : set.cards()) {
                assertTrue(words.add(card.dialect()), "한 낱말이 두 세트에 들어갔다: " + card.cardId());
            }
        }
        assertEquals(60, words.size());
    }

    @Test
    void 발행본의_식별자는_앞선_발행본과_겹치지_않는다() {
        // 시도 시작과 세트 상세는 세트를 현재 발행본에서만 찾는다 (WordLearningService). 식별자가 겹치면 배포 전에 받은
        // 세트 상세로 낸 답이 같은 식별자의 다른 문항으로 오류 없이 채점된다 (PR #73 리뷰). 겹치지 않으면 404나 422다.
        Set<String> previous = new HashSet<>();
        for (WordLearningDefinition.Set set : registry.find("wd-gn-2026.10.1").orElseThrow().definition().sets()) {
            previous.add(set.setId());
            set.cards().forEach(card -> previous.add(card.cardId()));
            for (WordLearningDefinition.Item item : set.items()) {
                previous.add(item.itemId());
                item.choices().forEach(choice -> previous.add(choice.choiceId()));
            }
        }
        for (WordLearningDefinition.Set set : registry.current().definition().sets()) {
            assertFalse(previous.contains(set.setId()), set.setId());
            set.cards().forEach(card -> assertFalse(previous.contains(card.cardId()), card.cardId()));
            for (WordLearningDefinition.Item item : set.items()) {
                assertFalse(previous.contains(item.itemId()), item.itemId());
                item.choices().forEach(choice -> assertFalse(previous.contains(choice.choiceId()), choice.choiceId()));
            }
        }
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

    // === 시간 초과 답안 (KAN-360) ===

    @Test
    void 시간_초과_답안은_보기_없는_오답으로_저장되고_결과와_오답_기록에_남는다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        List<WordLearningDefinition.Item> items = set.items();
        assertTrue(items.size() >= 3, "시간 초과, 고른 오답, 정답을 한 문항씩 낸다");
        WordLearningDefinition.Item timedOutItem = items.get(0);
        WordLearningDefinition.Item wrongItem = items.get(1);
        String wrongChoice = wrongItem.choices().stream().map(WordLearningDefinition.Choice::choiceId)
                .filter(id -> !id.equals(wrongItem.correctChoiceId())).findFirst().orElseThrow();

        // 보기 없이 timedOut만 보내면 오답이고 정답과 해설이 온다.
        JsonNode timedOut = body(timeout(access, attemptId, timedOutItem.itemId(), "k-timeout")
                .andExpect(status().isOk()));
        assertFalse(timedOut.get("correct").asBoolean());
        assertEquals(timedOutItem.correctChoiceId(), timedOut.get("correctChoiceId").asString());
        assertEquals(timedOutItem.correctText(), timedOut.get("correctText").asString());
        assertEquals(timedOutItem.explanation(), timedOut.get("explanation").asString());
        assertEquals(1, timedOut.get("answeredCount").asInt());

        // 답안 행에는 보기가 없고, 오답 기록에는 보기를 고른 오답과 같이 남는다.
        WordAttemptAnswer stored = answers.findByAttemptIdAndItemId(attemptId, timedOutItem.itemId()).orElseThrow();
        assertNull(stored.choiceId());
        assertTrue(stored.timedOut());
        assertFalse(stored.correct());
        assertEquals(1, wrongAnswers.findByUserIdAndContentVersionAndItemId(userIdOf(attemptId),
                set(attemptId).contentVersion(), timedOutItem.itemId()).orElseThrow().wrongCount());

        // 나머지 문항은 하나를 틀리고 그 밖은 맞힌다 - 시간 초과 문항이 있어도 세트를 완료할 수 있다.
        answer(access, attemptId, wrongItem.itemId(), "k-wrong", wrongChoice).andExpect(status().isOk());
        for (WordLearningDefinition.Item item : items.subList(2, items.size())) {
            answer(access, attemptId, item.itemId(), "k-" + item.itemId(), item.correctChoiceId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));
        }
        JsonNode result = body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isOk()));
        int itemCount = items.size();
        assertEquals(itemCount - 2, result.get("correctCount").asInt());
        assertEquals((int) Math.round((itemCount - 2) * 100.0 / itemCount), result.get("accuracyPercent").asInt());
        assertEquals(2, result.get("wrongItems").size());

        // 오답 항목은 문항 순이다. 시간 초과 문항은 고른 보기가 null이고 timedOut이 true다.
        JsonNode timedOutEntry = result.get("wrongItems").get(0);
        assertEquals(timedOutItem.itemId(), timedOutEntry.get("itemId").asString());
        assertTrue(timedOutEntry.has("chosenChoiceId"), "필드는 빠지지 않고 null로 온다");
        assertTrue(timedOutEntry.get("chosenChoiceId").isNull());
        assertTrue(timedOutEntry.has("chosenText"));
        assertTrue(timedOutEntry.get("chosenText").isNull());
        assertTrue(timedOutEntry.get("timedOut").asBoolean());
        assertEquals(timedOutItem.correctChoiceId(), timedOutEntry.get("correctChoiceId").asString());
        assertEquals(timedOutItem.correctText(), timedOutEntry.get("correctText").asString());
        assertEquals(timedOutItem.explanation(), timedOutEntry.get("explanation").asString());
        // 보기를 고른 오답은 종전 그대로이고 timedOut이 false다.
        JsonNode wrongEntry = result.get("wrongItems").get(1);
        assertEquals(wrongItem.itemId(), wrongEntry.get("itemId").asString());
        assertEquals(wrongChoice, wrongEntry.get("chosenChoiceId").asString());
        assertEquals(wrongItem.choice(wrongChoice).orElseThrow().text(), wrongEntry.get("chosenText").asString());
        assertFalse(wrongEntry.get("timedOut").asBoolean());

        // 완료 뒤에는 시간 초과 제출도 409이고, 완료를 다시 부르면 같은 본문이다.
        timeout(access, attemptId, timedOutItem.itemId(), "k-timeout")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEARNING_ATTEMPT_COMPLETED"));
        assertEquals(result, body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isOk())));
    }

    @Test
    void 전_문항이_시간_초과여도_세트를_완료하고_정답률은_0이다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        for (WordLearningDefinition.Item item : set.items()) {
            timeout(access, attemptId, item.itemId(), "k-" + item.itemId()).andExpect(status().isOk());
        }

        JsonNode result = body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), access)
                .andExpect(status().isOk()));
        assertEquals(0, result.get("correctCount").asInt());
        assertEquals(0, result.get("accuracyPercent").asInt());
        assertEquals(set.items().size(), result.get("wrongItems").size());
        for (JsonNode entry : result.get("wrongItems")) {
            assertTrue(entry.get("timedOut").asBoolean());
        }
    }

    @Test
    void timedOut과_choiceId의_조합은_정해진_대로_받거나_거절한다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item item = set.items().get(0);
        String choice = item.correctChoiceId();

        // timedOut이 true인데 choiceId도 있으면 400이다 - 빈 문자열도 값이 있는 것으로 본다.
        for (String both : List.of("{\"timedOut\":true,\"choiceId\":\"" + choice + "\"}",
                "{\"timedOut\":true,\"choiceId\":\"\"}")) {
            raw(access, attemptId, item.itemId(), "k", both)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        // timedOut이 없거나 false거나 null이면 지금처럼 choiceId가 필수다.
        for (String missing : List.of("{}", "{\"timedOut\":false}", "{\"timedOut\":null}",
                "{\"timedOut\":false,\"choiceId\":null}")) {
            raw(access, attemptId, item.itemId(), "k", missing)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertEquals(0, answers.countByAttemptId(attemptId));

        // 시간 초과도 이 세트의 문항이어야 한다.
        WordLearningDefinition.Item foreign = registry.current().definition().sets().get(1).items().get(0);
        timeout(access, attemptId, foreign.itemId(), "k")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_IN_VERSION"));
        // Idempotency-Key는 시간 초과에도 필수다.
        authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + item.itemId() + "/answer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"timedOut\":true}"), access)
                .andExpect(status().isBadRequest());
        assertEquals(0, answers.countByAttemptId(attemptId));

        // choiceId를 null로 명시한 시간 초과와, timedOut을 false로 명시한 보기 제출은 받는다.
        raw(access, attemptId, item.itemId(), "k-null", "{\"timedOut\":true,\"choiceId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false));
        WordLearningDefinition.Item second = set.items().get(1);
        raw(access, attemptId, second.itemId(), "k-false",
                "{\"timedOut\":false,\"choiceId\":\"" + second.correctChoiceId() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void 시간_초과도_같은_키의_재전송은_처음_결과이고_보기와_바꿔_보내면_400이다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item item = set.items().get(0);

        JsonNode first = body(timeout(access, attemptId, item.itemId(), "same-key").andExpect(status().isOk()));
        JsonNode replay = body(timeout(access, attemptId, item.itemId(), "same-key").andExpect(status().isOk()));
        assertEquals(first, replay);
        assertEquals(1, answers.countByAttemptId(attemptId));
        assertEquals(1, wrongAnswers.findByUserIdAndContentVersionAndItemId(userIdOf(attemptId),
                set(attemptId).contentVersion(), item.itemId()).orElseThrow().wrongCount(), "재전송은 오답을 세지 않는다");

        // 같은 키로 보기를 보내면 다른 답이라 400, 다른 키로 다시 내면 409다.
        answer(access, attemptId, item.itemId(), "same-key", item.correctChoiceId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        answer(access, attemptId, item.itemId(), "other-key", item.correctChoiceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_ALREADY_ANSWERED"));
        timeout(access, attemptId, item.itemId(), "other-key")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_ALREADY_ANSWERED"));

        // 반대 방향도 같다 - 보기로 낸 문항에 같은 키로 시간 초과를 보내면 400이다.
        WordLearningDefinition.Item chosen = set.items().get(1);
        answer(access, attemptId, chosen.itemId(), "chosen-key", chosen.correctChoiceId()).andExpect(status().isOk());
        timeout(access, attemptId, chosen.itemId(), "chosen-key")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertTrue(answers.findByAttemptIdAndItemId(attemptId, chosen.itemId()).orElseThrow().correct());
        assertNull(answers.findByAttemptIdAndItemId(attemptId, item.itemId()).orElseThrow().choiceId());
        assertEquals(2, answers.countByAttemptId(attemptId));
    }

    @Test
    void 마이그레이션_뒤에도_보기가_있는_기존_답안_행은_그대로_읽히고_보기_열만_null을_허용한다() throws Exception {
        String access = signUp();
        WordLearningDefinition.Set set = firstSet();
        String attemptId = startAttempt(access, set.setId());
        WordLearningDefinition.Item item = set.items().get(0);
        // 마이그레이션 전 모양의 행(보기가 있는 답안)을 표에 직접 넣는다.
        jdbc.update("""
                        insert into word_attempt_answer
                            (id, attempt_id, item_id, choice_id, is_correct, idempotency_key, created_at)
                        values (?, ?, ?, ?, true, 'legacy-key', now())
                        """, "waa_" + UUID.randomUUID(), attemptId, item.itemId(), item.correctChoiceId());

        WordAttemptAnswer stored = answers.findByAttemptIdAndItemId(attemptId, item.itemId()).orElseThrow();
        assertEquals(item.correctChoiceId(), stored.choiceId());
        assertFalse(stored.timedOut());
        assertTrue(stored.correct());
        // 같은 키의 재전송으로도 읽힌다.
        answer(access, attemptId, item.itemId(), "legacy-key", item.correctChoiceId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));

        assertEquals("YES", jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = 'word_attempt_answer' and column_name = 'choice_id'
                """, String.class));
        // 다른 열과 레벨테스트 어휘 답안의 보기 열은 그대로 NOT NULL이다.
        assertEquals("NO", jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = 'word_attempt_answer' and column_name = 'is_correct'
                """, String.class));
        assertEquals("NO", jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = 'vocab_answer' and column_name = 'choice_id'
                """, String.class));
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

    /** 시간 초과 답안 - 보기 없이 timedOut만 싣는다 (KAN-360). */
    private ResultActions timeout(String access, String attemptId, String itemId, String key) throws Exception {
        return raw(access, attemptId, itemId, key, "{\"timedOut\":true}");
    }

    private ResultActions raw(String access, String attemptId, String itemId, String key, String json)
            throws Exception {
        return authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + itemId + "/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content(json), access);
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
