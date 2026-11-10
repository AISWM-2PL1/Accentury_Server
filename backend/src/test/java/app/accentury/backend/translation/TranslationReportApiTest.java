package app.accentury.backend.translation;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.feedback.SlackWebhookClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 번역 결과 신고의 실행 가능한 명세 (KAN-343, 명세서 §3.22) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로 돈다.
 * <p>
 * 슬랙은 기록용 클라이언트로 바꿔 두어 네트워크에 나가지 않는다. 전송이 비동기라(후기와 같은 전용 실행기) 단언 전에
 * 기다린다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = {
        "accentury.auth.fake-idp=true",
        // 실제 채널의 URL이 아니라 형식만 흉내 낸 가짜다 - 값이 "있다"는 것만이 이 설정의 몫이다.
        "accentury.feedback.slack-webhook-url=https://hooks.slack.com/services/T000/B000/test"})
@ExtendWith(OutputCaptureExtension.class)
class TranslationReportApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;
    private static final Duration WAIT = Duration.ofSeconds(5);

    @TestConfiguration
    static class RecordingClientConfig {

        @Bean
        @Primary
        RecordingSlackWebhookClient recordingSlackWebhookClient() {
            return new RecordingSlackWebhookClient();
        }
    }

    /** 보낸 메시지를 모으는 클라이언트. */
    static class RecordingSlackWebhookClient implements SlackWebhookClient {

        private final List<String> posted = new ArrayList<>();

        @Override
        public void post(String text) {
            synchronized (posted) {
                posted.add(text);
            }
        }

        List<String> snapshot() {
            synchronized (posted) {
                return List.copyOf(posted);
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TranslationReports reports;

    @Autowired
    private RecordingSlackWebhookClient slack;

    // === 저장과 알림 (AC 3) ===

    @Test
    void 신고하면_201이고_저장되고_슬랙으로_한_번_나간다() throws Exception {
        String access = signUp();
        UUID userId = userId(access);

        JsonNode response = body(report(access, "toStandard", "  밥 뭇나?  ", " 밥 먹었어? ")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));

        UUID reportId = UUID.fromString(response.get("reportId").asString());
        TranslationReports.Report saved = reports.find(reportId).orElseThrow();
        assertEquals(userId, saved.userId());
        assertEquals("toStandard", saved.direction());
        assertEquals("밥 뭇나?", saved.sourceText(), "앞뒤 공백은 뺀다");
        assertEquals("밥 먹었어?", saved.resultText());

        String message = awaitSlack(reportId);
        assertTrue(message.contains("번역 결과 신고 toStandard"), message);
        assertTrue(message.contains("신고 " + reportId), message);
        assertTrue(message.contains("계정 " + userId.toString().substring(0, 8)), message);
        assertFalse(message.contains(userId.toString()), "계정 id 전체는 싣지 않는다");
        assertTrue(message.contains("원문:\n> 밥 뭇나?"), message);
        assertTrue(message.contains("결과:\n> 밥 먹었어?"), message);
    }

    @Test
    void direction이_없으면_toDialect다() throws Exception {
        String access = signUp();

        JsonNode response = body(report(access, null, "밥 먹었어?", "밥 뭇나?").andExpect(status().isCreated()));

        assertEquals("toDialect",
                reports.find(UUID.fromString(response.get("reportId").asString())).orElseThrow().direction());
    }

    /** 번역기를 쓰는 계정은 누구나 신고할 수 있어야 한다 - 게스트 계정(KAN-329)이 쓰는 기능이 번역기다. */
    @Test
    void 게스트_계정도_신고할_수_있다() throws Exception {
        report(guest(), "toDialect", "밥 먹었어?", "밥 뭇나?").andExpect(status().isCreated());
    }

    @Test
    void 슬랙_문법이_든_문장은_이스케이프해서_올린다() throws Exception {
        String access = signUp();

        JsonNode response = body(report(access, "toDialect", "<!channel> 밥 & 국", "<http://x.test|눌러>")
                .andExpect(status().isCreated()));

        String message = awaitSlack(UUID.fromString(response.get("reportId").asString()));
        assertTrue(message.contains("&lt;!channel&gt; 밥 &amp; 국"), message);
        assertFalse(message.contains("<!channel>"), "신고가 채널 전체 멘션이 되면 안 된다");
        assertFalse(message.contains("<http://x.test|눌러>"), message);
    }

    // === 인증과 검증 ===

    @Test
    void 토큰이_없으면_401이고_저장도_알림도_없다() throws Exception {
        long before = count();

        mockMvc.perform(post("/v0/translations/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"밥 먹었어?\", \"result\": \"밥 뭇나?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));

        assertEquals(before, count());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "toDialect, NULL, 밥 뭇나?",          // 원문 없음
            "toDialect, '   ', 밥 뭇나?",          // 공백뿐인 원문
            "toDialect, 밥 먹었어?, NULL",         // 결과 없음
            "toDialect, 밥 먹었어?, ''",           // 빈 결과
            "sideways, 밥 먹었어?, 밥 뭇나?",      // 모르는 방향
    }, nullValues = "NULL")
    void 형식이_틀리면_400이고_저장하지_않는다(String direction, String text, String result) throws Exception {
        String access = signUp();
        long before = count();

        report(access, direction, text, result)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(before, count());
    }

    @Test
    void 길이는_원문_100자와_결과_300자까지다() throws Exception {
        String access = signUp();

        report(access, "toDialect", "가".repeat(100), "나".repeat(300)).andExpect(status().isCreated());
        report(access, "toDialect", "가".repeat(101), "밥 뭇나?").andExpect(status().isBadRequest());
        report(access, "toDialect", "밥 먹었어?", "나".repeat(301)).andExpect(status().isBadRequest());
    }

    @Test
    void 본문이_없으면_400이다() throws Exception {
        mockMvc.perform(post("/v0/translations/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + signUp())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // === 요청 제한 (§2.5) ===

    @Test
    void 계정당_분당_한도를_넘으면_429이고_다른_계정은_영향이_없다() throws Exception {
        String access = signUp();
        for (int i = 0; i < AccenturyProperties.Translation.REPORT_RATE_LIMIT_PER_MINUTE; i++) {
            report(access, "toDialect", "밥 먹었어?", "밥 뭇나?").andExpect(status().isCreated());
        }

        report(access, "toDialect", "밥 먹었어?", "밥 뭇나?")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        report(signUp(), "toDialect", "밥 먹었어?", "밥 뭇나?").andExpect(status().isCreated());
    }

    // === 탈퇴와 보존 기간 (2026-11-10 결정) ===

    @Test
    void 탈퇴하면_신고는_남고_계정_연결만_끊긴다() throws Exception {
        String access = signUp();
        UUID reportId = UUID.fromString(body(report(access, "toDialect", "밥 먹었어?", "밥 뭇나?"))
                .get("reportId").asString());

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent());

        TranslationReports.Report kept = reports.find(reportId).orElseThrow();
        assertNull(kept.userId(), "누구의 신고인지 알 수 없게 된다");
        assertEquals("밥 먹었어?", kept.sourceText(), "신고 내용은 보존 기간까지 남는다");
    }

    @Test
    void 게스트_계정이_탈퇴해도_신고의_계정_연결이_끊긴다() throws Exception {
        String access = guest();
        UUID reportId = UUID.fromString(body(report(access, "toDialect", "밥 먹었어?", "밥 뭇나?"))
                .get("reportId").asString());

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent());

        assertNull(reports.find(reportId).orElseThrow().userId());
    }

    @Test
    void 보존_기간이_지난_신고만_지운다() {
        Instant now = Instant.parse("2027-01-01T00:00:00Z");
        Duration retention = AccenturyProperties.Translation.REPORT_RETENTION;
        UUID expired = insert(now.minus(retention).minusSeconds(1));
        UUID onBoundary = insert(now.minus(retention));
        UUID fresh = insert(now.minus(Duration.ofDays(1)));

        new TranslationReportRetention(reports, Clock.fixed(now, ZoneOffset.UTC)).purgeExpired();

        assertTrue(reports.find(expired).isEmpty());
        assertTrue(reports.find(onBoundary).isPresent(), "정확히 보존 기간 전의 행은 아직 남는다");
        assertTrue(reports.find(fresh).isPresent());
    }

    // === 로그 (§2.6) ===

    @Test
    void 신고한_문장은_로그에_남지_않는다(CapturedOutput output) throws Exception {
        report(signUp(), "toDialect", "로그에남으면안되는원문", "로그에남으면안되는결과").andExpect(status().isCreated());

        String logs = output.getAll();
        assertTrue(logs.contains("번역 신고 저장 reportId="), "저장 로그는 남아야 한다");
        assertFalse(logs.contains("로그에남으면안되는원문"));
        assertFalse(logs.contains("로그에남으면안되는결과"));
    }

    private UUID insert(Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into translation_report (id, user_id, direction, source_text, result_text, created_at)
                        values (:id, null, 'toDialect', '밥 먹었어?', '밥 뭇나?', :createdAt)
                        """)
                .param("id", id)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
        return id;
    }

    private long count() {
        return jdbc.sql("select count(*) from translation_report").query(Long.class).single();
    }

    /**
     * 그 신고의 알림이 올 때까지 기다린다. 개수가 아니라 신고 id로 찾는다 - 전송이 비동기라 앞 테스트가 낸 알림이
     * 이 테스트 도중에 도착할 수 있고, 개수로 세면 남의 알림을 집는다 (Codex 리뷰 P2).
     */
    private String awaitSlack(UUID reportId) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> matched = slack.snapshot().stream()
                    .filter(message -> message.contains("신고 " + reportId)).toList();
            if (!matched.isEmpty()) {
                assertEquals(1, matched.size(), "같은 신고가 두 번 올라가면 안 된다");
                return matched.getFirst();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("슬랙 알림이 오지 않았다 reportId=" + reportId);
    }

    private ResultActions report(String access, String direction, String text, String result) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (direction != null) {
            body.put("direction", direction);
        }
        if (text != null) {
            body.put("text", text);
        }
        if (result != null) {
            body.put("result", result);
        }
        return mockMvc.perform(post("/v0/translations/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID userId(String access) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("user").get("id").asString());
    }

    /** 게스트 계정을 하나 만든다 - 발급 상한이 IP당 하루 단위라 부를 때마다 다른 IP다. */
    private String guest() throws Exception {
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
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    private String signUp() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:sub-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", POLICY_VERSION);
        String body = mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asString();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", "user@example.com");
        profile.put("name", "박유현");
        profile.put("birthDate", "1999-03-02");
        profile.put("region", "GYEONGNAM");
        mockMvc.perform(put("/v0/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());
        return access;
    }
}
