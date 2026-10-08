package app.accentury.backend.translation;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AppUserRepository;
import app.accentury.backend.common.AccenturyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 번역 기록의 배선과 대체 ID 수명 (KAN-266, 명세서 §3.18) - 기록 버킷이 없는 배포(staging)의 동작, S3 저장 실패와 번역
 * 응답의 분리, 탈퇴 때 대응표 행 삭제.
 * <p>
 * 이 클래스는 기록 버킷 설정이 없다 - 그래서 컨텍스트에 {@link S3TranslationRecordStore}가 없고 서비스는
 * {@link TranslationRecordStore#NONE}을 쓴다. S3 실패 경로는 같은 서비스를 실패하는 S3 저장소로 직접 조립해 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class TranslationRecordWiringApiTest extends IntegrationTest {

    @TestConfiguration
    static class FakeTranslatorConfig {

        @Bean
        @Primary
        DialectTranslator fakeTranslator() {
            return new DialectTranslator() {
                @Override
                public Reply translate(String text) {
                    return Reply.translated("밥 뭇나?");
                }

                @Override
                public String model() {
                    return "fake-model";
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TranslationSubjects subjects;

    @Autowired
    private DialectTranslator translator;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AppUserRepository users;

    @Autowired(required = false)
    private TranslationRecordStore recordStore;

    @Test
    void 기록_버킷이_없으면_번역은_되고_저장도_대체_ID도_없다() throws Exception {
        assertNull(recordStore, "버킷이 없는 배포에는 기록 저장소 빈이 없다");
        String access = signUp();

        translate(access, "밥 먹었어?").andExpect(status().isOk()).andExpect(jsonPath("$.dialect").value("밥 뭇나?"));

        assertEquals(0, jdbc.queryForObject("select count(*) from translation_subject", Integer.class));
    }

    @Test
    void S3_저장이_실패해도_번역_응답은_정상이다() throws Exception {
        S3Client failing = mock(S3Client.class);
        when(failing.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());
        // 실행기를 호출 스레드로 두어 저장 실패가 응답 경로 안에서 일어나게 한다 - 그래도 응답이 정상이어야 한다.
        S3TranslationRecordStore store = new S3TranslationRecordStore(failing, "accentury-translator-prompt-test",
                subjects, objectMapper, Clock.systemUTC(), Runnable::run, meterRegistry);
        TranslationService service = new TranslationService(translator, store, Clock.systemUTC(), meterRegistry);

        TranslationResponse response = service.translate(user(), new TranslationRequest("밥 먹었어?"));

        assertEquals("밥 뭇나?", response.dialect());
    }

    @Test
    void 대체_ID는_계정당_하나이고_탈퇴하면_대응표_행만_지워진다() throws Exception {
        String access = signUp();
        UUID userId = userId(access);

        UUID first = subjects.resolve(userId, Instant.now());
        assertNotNull(first);
        assertEquals(first, subjects.resolve(userId, Instant.now()), "같은 계정은 같은 대체 ID다");

        mockMvc.perform(post("/v0/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("select count(*) from translation_subject where user_id = ?",
                Integer.class, userId));
        // 탈퇴 뒤의 늦은 기록이 대체 ID를 다시 만들지 않는다 - 그 기록은 누구의 것인지 알 수 없게 남는다.
        assertNull(subjects.resolve(userId, Instant.now()));
        assertEquals(0, jdbc.queryForObject("select count(*) from translation_subject", Integer.class));
    }

    // === 도우미 ===

    private AppUser user() throws Exception {
        return users.findById(userId(signUp())).orElseThrow();
    }

    private ResultActions translate(String access, String text) throws Exception {
        return mockMvc.perform(post("/v0/translations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", text))));
    }

    private UUID userId(String access) throws Exception {
        String body = mockMvc.perform(get("/v0/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("user").path("id").asString());
    }

    private String signUp() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:sub-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", AccenturyProperties.Auth.PRIVACY_POLICY_VERSION);
        String body = mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asString();
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
