package app.accentury.backend.auth;

import app.accentury.backend.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh 저장소(Redis)가 죽었을 때 (KAN-223 AC 8, NFR-AV-02) - 로그인과 refresh만 503이고 익명 응시와 헬스체크는
 * 멀쩡해야 한다.
 * <p>
 * Redis 컨테이너를 import하지 않고 아무도 듣지 않는 포트를 가리킨다 - "Redis를 내린" 상태와 같다. 헬스체크가 UP인
 * 것이 핵심이다: Redis가 집계 health에 들어가 있으면 ALB가 backend 태스크를 전부 빼서 응시까지 멈춘다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "accentury.auth.fake-idp=true",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=500ms",
        "spring.data.redis.timeout=500ms"})
class AuthStoreDownApiTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로그인은_503이다() throws Exception {
        mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"KAKAO\", \"accessToken\": \"fake:store-down\","
                                + " \"privacyConsent\": true, \"privacyPolicyVersion\": \"2026-09-24\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void refresh는_503이다() throws Exception {
        mockMvc.perform(post("/v0/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"rt_" + "A".repeat(43) + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_STORE_UNAVAILABLE"));
    }

    @Test
    void 익명_세션_생성과_헬스체크는_정상이다() throws Exception {
        mockMvc.perform(post("/v0/sessions")).andExpect(status().isCreated());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
