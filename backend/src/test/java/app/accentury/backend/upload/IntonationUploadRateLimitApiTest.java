package app.accentury.backend.upload;

import app.accentury.backend.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 억양 학습 녹음 업로드의 IP 단위 제한 (KAN-299, 명세서 §2.5).
 * <p>
 * 계정 토큰 검사는 컨트롤러 인자 해석 단계라 multipart 해석 뒤에 돈다 - 미인증 요청도 본문을 받아
 * 버리므로 {@link UploadRateLimitFilter}가 해석 전에 IP로 끊어야 한다. 그래서 여기서는 토큰 없이 보내고,
 * 한도 안에서는 필터를 지나 401(토큰 없음)에 닿고 한도를 넘으면 429가 나는 것을 본다. 레벨테스트
 * 업로드의 IP 통({@link UploadRateLimitApiTest})과는 별개다.
 */
@SpringBootTest(properties = "accentury.learning.intonation-ip-rate-limit-per-minute=2")
@AutoConfigureMockMvc
class IntonationUploadRateLimitApiTest extends IntegrationTest {

    private static final String VALID_META = """
            {"durationMs": 3000,
             "clientQuality": {"rms": 0.11, "peak": 0.83, "silenceRatio": 0.12, "clipped": false}}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 한도_안에서는_필터를_지나_토큰_검사에_닿고_넘으면_429다() throws Exception {
        mockMvc.perform(upload("key-1", "9.9.7.1", "recordings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(upload("key-2", "9.9.7.1", "recordings")).andExpect(status().isUnauthorized());

        mockMvc.perform(upload("key-3", "9.9.7.1", "recordings"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retryAfterMs").isNumber())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void 인코딩이나_matrix_파라미터_경로로도_제한을_우회할_수_없다() throws Exception {
        // MVC는 %72ecordings와 recordings;x=1을 모두 recordings로 라우팅한다 - 필터도 같은 정규화로 매칭한다.
        mockMvc.perform(upload("evade-1", "9.9.7.2", "recordings")).andExpect(status().isUnauthorized());
        mockMvc.perform(upload("evade-2", "9.9.7.2", "recordings")).andExpect(status().isUnauthorized());

        mockMvc.perform(upload("evade-3", "9.9.7.2", "%72ecordings")).andExpect(status().isTooManyRequests());
        mockMvc.perform(upload("evade-4", "9.9.7.2", "recordings;x=1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void 다른_IP와_레벨테스트_업로드_통은_영향을_받지_않는다() throws Exception {
        mockMvc.perform(upload("fill-1", "9.9.7.3", "recordings")).andExpect(status().isUnauthorized());
        mockMvc.perform(upload("fill-2", "9.9.7.3", "recordings")).andExpect(status().isUnauthorized());
        mockMvc.perform(upload("fill-3", "9.9.7.3", "recordings")).andExpect(status().isTooManyRequests());

        mockMvc.perform(upload("other-ip", "9.9.7.4", "recordings")).andExpect(status().isUnauthorized());

        // 같은 IP의 레벨테스트 업로드는 다른 통(VOICE_UPLOAD_IP)이라 학습 홍수에 막히지 않는다.
        SessionHandle session = createSession();
        mockMvc.perform(levelTestUpload(session, "level-1", "9.9.7.3")).andExpect(status().isAccepted());
    }

    // === 헬퍼 ===

    private record SessionHandle(String id, String token) {
    }

    private SessionHandle createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/v0/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new SessionHandle(json.get("sessionId").asString(), json.get("sessionToken").asString());
    }

    /** 토큰 없는 억양 학습 업로드. URI.create - 문자열 템플릿은 %72 같은 사전 인코딩을 재인코딩한다. */
    private RequestBuilder upload(String idempotencyKey, String clientIp, String lastSegment) {
        return multipart(URI.create("/v0/learning/intonation-cards/in-01/" + lastSegment))
                .file(new MockMultipartFile("audio", "recording.wav", "audio/wav",
                        WavFixtures.standardWav(3000)))
                .file(new MockMultipartFile("meta", "", "application/json",
                        VALID_META.getBytes(StandardCharsets.UTF_8)))
                .header("Idempotency-Key", idempotencyKey)
                // 첫 값은 클라이언트 위조분, 마지막 값이 프록시가 붙인 실제 IP다 (UploadRateLimitApiTest와 같다).
                .header("X-Forwarded-For", "203.0.113.99, " + clientIp);
    }

    private RequestBuilder levelTestUpload(SessionHandle session, String idempotencyKey, String clientIp) {
        return multipart(URI.create("/v0/sessions/" + session.id() + "/voice-items/v1/recording"))
                .file(new MockMultipartFile("audio", "recording.wav", "audio/wav",
                        WavFixtures.standardWav(3000)))
                .file(new MockMultipartFile("meta", "", "application/json",
                        VALID_META.getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token())
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Forwarded-For", "203.0.113.99, " + clientIp);
    }
}
