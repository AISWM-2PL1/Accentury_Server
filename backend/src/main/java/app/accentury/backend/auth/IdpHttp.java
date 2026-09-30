package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 카카오와 네이버의 사용자 조회 API 호출 (명세서 §3.9). SDK access token을 {@code Authorization: Bearer}로 싣고
 * JSON을 받는다. 네이버 토큰 교환(KAN-243)과 애플 토큰 교환과 revoke(KAN-241)는 form POST로 부른다.
 * <p>
 * 실패 분류가 이 클래스의 일이다: IdP가 4xx로 답하면 토큰이 틀린 것(401 {@code AUTH_IDP_TOKEN_INVALID})이고,
 * 429(호출 한도 초과)이거나 5xx이거나 닿지 못했거나 시간이 넘었거나 본문이 JSON이 아니면 IdP 장애(502 {@code AUTH_IDP_UNAVAILABLE})다.
 * 응답 본문은 로그에 남기지 않는다 - 이메일, 이름, 생년월일이 들어 있다 (§2.6).
 */
final class IdpHttp {

    private static final Logger log = LoggerFactory.getLogger(IdpHttp.class);

    private final String idpName;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    IdpHttp(String idpName, String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        this.idpName = idpName;
        this.objectMapper = objectMapper;
        // 이 클라이언트 전용 RestClient - Boot의 RestClient.Builder 자동 구성은 webmvc 스타터에 없어 정적 빌더로
        // 조립한다 (RestSlackWebhookClient, AnalysisDispatchConfig와 같은 이유).
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * @throws ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는 502 {@code AUTH_IDP_UNAVAILABLE}
     */
    JsonNode get(String path, String accessToken) {
        return exchange(path, () -> restClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(String.class));
    }

    /**
     * {@code application/x-www-form-urlencoded} POST - 네이버 토큰 교환(KAN-243)이 쓴다. 시크릿을 쿼리 문자열이 아니라
     * 본문에 실어 접근 로그와 프록시에 남지 않게 한다. 실패 분류는 {@link #get}과 같다.
     *
     * @throws ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는 502 {@code AUTH_IDP_UNAVAILABLE}
     */
    JsonNode postForm(String path, MultiValueMap<String, String> form) {
        return exchange(path, () -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));
    }

    /**
     * 응답 본문이 없는 form POST - 애플 토큰 revoke(KAN-241)가 쓴다. 애플은 성공을 빈 본문의 200으로 답한다.
     * 실패 분류는 {@link #get}과 같다.
     *
     * @throws ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는 502 {@code AUTH_IDP_UNAVAILABLE}
     */
    void postFormWithoutBody(String path, MultiValueMap<String, String> form) {
        send(path, () -> {
            restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private JsonNode exchange(String path, Supplier<@Nullable String> call) {
        String body = send(path, call);
        if (body == null) {
            log.warn("{} {} 빈 응답", idpName, path);
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            log.warn("{} {} 응답이 JSON이 아니다", idpName, path);
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        }
    }

    private @Nullable String send(String path, Supplier<@Nullable String> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429는 토큰이 틀린 것이 아니라 우리 앱의 IdP 호출 쿼터가 찬 것이다 - 401로 내면 앱이 멀쩡한 토큰을 버리고
            // 사용자를 다시 로그인시킨다. 잠시 뒤 재시도할 상류 장애로 낸다 (PR #2 리뷰).
            log.warn("{} {} 호출 한도 초과 status=429", idpName, path);
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        } catch (HttpClientErrorException e) {
            // 상태 코드만 남긴다 - 오류 본문에 토큰 조각이 되돌아오는 IdP가 있을 수 있다.
            log.info("{} {} 거절 status={}", idpName, path, e.getStatusCode().value());
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        } catch (RestClientException e) {
            log.warn("{} {} 호출 실패 - {}", idpName, path, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        }
    }
}
