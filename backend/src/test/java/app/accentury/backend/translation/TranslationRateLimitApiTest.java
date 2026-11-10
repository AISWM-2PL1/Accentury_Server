package app.accentury.backend.translation;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 번역 요청의 계정당 분당 제한 (KAN-322, 명세서 §2.5) - 설정 키가 축까지 닿는지와 429 봉투를 실제 경로로 본다. 칸과 기록의
 * 규칙은 {@link TranslationServiceQuotaTest}가 본다.
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = {"accentury.auth.fake-idp=true", "accentury.translation.rate-limit-per-minute=2"})
class TranslationRateLimitApiTest extends IntegrationTest {

    @TestConfiguration
    static class FakeTranslatorConfig {

        @Bean
        @Primary
        DialectTranslator fakeTranslator() {
            return new DialectTranslator() {
                @Override
                public Reply translate(String text, TranslationDirection direction) {
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

    @Test
    void 계정당_분당_한도를_넘으면_429_RATE_LIMITED이고_다른_계정은_영향이_없다() throws Exception {
        String access = signUp();
        translate(access).andExpect(status().isOk());
        translate(access).andExpect(status().isOk());

        translate(access)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retryAfterMs").isNumber())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        translate(signUp()).andExpect(status().isOk());
    }

    /** 게스트 계정(KAN-329)도 같은 축이다 - 계정 id가 키라 게스트라고 풀리지 않는다. */
    @Test
    void 게스트_계정도_분당_한도를_넘으면_429_RATE_LIMITED다() throws Exception {
        String body = mockMvc.perform(post("/v0/auth/guest")
                        .with(request -> {
                            request.setRemoteAddr("10.77.1.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("privacyConsent", true,
                                "privacyPolicyVersion", AccenturyProperties.Auth.PRIVACY_POLICY_VERSION))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asString();
        translate(access).andExpect(status().isOk());
        translate(access).andExpect(status().isOk());

        translate(access)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    private ResultActions translate(String access) throws Exception {
        return mockMvc.perform(post("/v0/translations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "밥 먹었어?"))));
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
