package app.accentury.backend.learning;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.testdefinition.TestDefinition;
import app.accentury.backend.testdefinition.TestDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 억양 학습 콘텐츠 API의 실행 가능한 명세 (KAN-264, 명세서 §3.17) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * 기대값은 발행본 {@code in-gn-2026.11.1}(V20, KAN-345)을 {@link IntonationLearningRegistry}에서 읽는다 - 발행본이 재발행되면
 * 테스트가 조용히 옛 내용을 검사하지 않게 하려는 것이다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class IntonationLearningApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntonationLearningRegistry registry;

    @Autowired
    private TestDefinitionRegistry testDefinitions;

    @Test
    void 토큰이_없으면_두_경로_모두_401이다() throws Exception {
        mockMvc.perform(get("/v0/learning/intonation-courses")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/v0/learning/intonation-courses/" + firstCourse().courseId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void 로그인한_계정은_코스_목록부터_카드까지_받는다() throws Exception {
        String access = signUp();
        IntonationLearningDefinition current = registry.current().definition();

        JsonNode list = body(authed(get("/v0/learning/intonation-courses"), access)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        assertEquals(current.contentVersion(), list.get("contentVersion").asString());
        assertEquals(current.courses().size(), list.get("courses").size());
        JsonNode first = list.get("courses").get(0);
        IntonationLearningDefinition.Course course = firstCourse();
        assertEquals(course.courseId(), first.get("courseId").asString());
        assertEquals(1, first.get("seq").asInt());
        assertEquals(course.level(), first.get("level").asInt());
        assertEquals(course.title(), first.get("title").asString());
        assertEquals(course.cards().size(), first.get("cardCount").asInt());
        assertTrue(first.has("topic") && first.get("topic").isNull(), "주제가 없어도 필드는 null로 있다");

        JsonNode detail = body(authed(get("/v0/learning/intonation-courses/" + course.courseId()), access)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        assertEquals(current.contentVersion(), detail.get("contentVersion").asString());
        assertEquals(course.cards().size(), detail.get("cards").size());
        JsonNode card = detail.get("cards").get(0);
        IntonationLearningDefinition.Card expected = course.cards().get(0);
        assertEquals(expected.cardId(), card.get("cardId").asString());
        assertEquals(expected.dialect(), card.get("dialect").asString());
        // AC 2 - 표준어 원문과 기준 음원 URL은 값이 없으면 null로 온다 (2026-10-08 결정).
        assertTrue(card.has("standard") && card.get("standard").isNull());
        assertTrue(card.has("referenceAudioUrl") && card.get("referenceAudioUrl").isNull());
        // 가이드 곡선은 테스트 정의 문항과 같은 모양이다 - 무성 구간 null 원소까지 그대로다.
        JsonNode guide = card.get("guideF0");
        assertEquals(expected.guideF0().unit(), guide.get("unit").asString());
        assertEquals(expected.guideF0().frameIntervalMs(), guide.get("frameIntervalMs").asInt());
        assertEquals(expected.guideF0().values().size(), guide.get("values").size());
        assertFalse(guide.has("bandLow"), "밴드가 없는 곡선에는 밴드 필드를 싣지 않는다");
        // AI 채점용 대사 식별자는 서버 안에만 있다.
        assertFalse(card.has("scriptKey"));
    }

    @Test
    void 없는_코스는_404다() throws Exception {
        authed(get("/v0/learning/intonation-courses/nope"), signUp())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEARNING_COURSE_NOT_FOUND"));
    }

    @Test
    void 발행본은_구간_다섯에_코스_다섯씩_카드_세_장씩_싣는다() {
        IntonationLearningDefinition current = registry.current().definition();
        assertEquals("in-gn-2026.11.1", current.contentVersion());
        // KAN-345 - 앱 학습 탭의 구간이 level이다. 구간마다 코스 5개, 코스마다 카드 3장이고 seq 순서가 쉬운 순이다.
        assertEquals(25, current.courses().size());
        Set<String> scriptKeys = new HashSet<>();
        for (IntonationLearningDefinition.Course course : current.courses()) {
            assertEquals((course.seq() - 1) / 5 + 1, course.level(), course.courseId());
            assertEquals(3, course.cards().size(), course.courseId());
            for (IntonationLearningDefinition.Card card : course.cards()) {
                assertTrue(scriptKeys.add(card.scriptKey()), "한 문장이 두 코스에 들어갔다: " + card.cardId());
            }
        }
        assertEquals(75, scriptKeys.size());
    }

    @Test
    void 발행본의_카드는_전부_레벨테스트_음성_풀의_문항이다() {
        // 새 문장을 넣지 않는다 (KAN-345) - 대사, scriptKey, 가이드 곡선이 원천 정의 gn-2026.10.2의 VOICE 문항 그대로다.
        Map<String, TestDefinition.Item> pool = new LinkedHashMap<>();
        for (TestDefinition.Item item : testDefinitions.get("gn-2026.10.2").definition().items()) {
            if (item.scriptKey() != null) {
                pool.put(item.scriptKey(), item);
            }
        }
        for (IntonationLearningDefinition.Course course : registry.current().definition().courses()) {
            for (IntonationLearningDefinition.Card card : course.cards()) {
                TestDefinition.Item source = pool.get(card.scriptKey());
                assertNotNull(source, "레벨테스트 음성 풀에 없는 문장이다: " + card.cardId());
                assertEquals(source.prompt(), card.dialect(), card.cardId());
                assertEquals(source.guideF0(), card.guideF0(), card.cardId());
            }
        }
    }

    @Test
    void 발행본의_식별자는_앞선_발행본과_겹치지_않는다() {
        // 녹음 업로드는 카드를 현재 발행본에서만 찾는다 (IntonationScoringService). 식별자가 겹치면 배포 전에 옛 코스를
        // 연 사람의 녹음이 같은 식별자의 다른 문장으로 오류 없이 채점된다 (PR #73 리뷰). 겹치지 않으면 404다.
        Set<String> previous = new HashSet<>();
        for (IntonationLearningDefinition.Course course
                : registry.find("in-gn-2026.10.1").orElseThrow().definition().courses()) {
            previous.add(course.courseId());
            course.cards().forEach(card -> previous.add(card.cardId()));
        }
        for (IntonationLearningDefinition.Course course : registry.current().definition().courses()) {
            assertFalse(previous.contains(course.courseId()), course.courseId());
            course.cards().forEach(card -> assertFalse(previous.contains(card.cardId()), card.cardId()));
        }
    }

    private IntonationLearningDefinition.Course firstCourse() {
        return registry.current().definition().courses().get(0);
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
