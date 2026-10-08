package app.accentury.backend.translation;

import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gemini API {@code generateContent} 호출 (KAN-266, 명세서 §3.18).
 * <p>
 * <b>시간 상한은 호출 전체에 건다</b> (2026-10-08 결정 - 동기 응답, 10초). 시도마다 응답 본문을 다 받을 때까지를 남은
 * 시간으로 기다린다 - {@link HttpRequest.Builder#timeout}은 응답 헤더까지만 지키고 본문이 느리게 오면 끝없이 기다리므로
 * (Codex astra 리뷰 P2), 비동기로 보내고 그 결과를 남은 시간만큼만 기다린 뒤 넘으면 요청을 취소한다. 다시
 * 부르는 것은 연결 실패, 5xx, 시간 초과에만 1회다 - 남은 시간이 {@link #MIN_RETRY_BUDGET}보다 적으면 다시 부르지 않는다.
 * 429(무료 한도 소진)는 다시 불러도 같으니 바로 실패다. 그래서 RestClient가 아니라 요청마다 시간을 정할 수 있는 JDK
 * {@link HttpClient}를 직접 쓴다.
 * <p>
 * 키는 헤더 {@code x-goog-api-key}로만 보낸다 - 쿼리 문자열에 실으면 예외 메시지와 프록시 로그에 URI째 남는다.
 * 요청 본문과 응답 본문은 로그에 남기지 않는다 (입력과 번역 텍스트가 들어 있다, 명세서 §2.6).
 * <p>
 * 답은 JSON 스키마로 묶는다({@code responseSchema}) - {@code status}가 {@code TRANSLATED}나 {@code REJECTED}이고
 * {@code dialect}가 번역 문장이다. 그래도 스키마를 벗어난 답은 {@link DialectTranslator.Kind#MALFORMED}로 돌려
 * 번역 불가가 되게 한다 - 사업자의 스키마 강제도 믿지 않는다.
 */
class GeminiDialectTranslator implements DialectTranslator {

    private static final Logger log = LoggerFactory.getLogger(GeminiDialectTranslator.class);

    /** 재시도를 시작하려면 남아 있어야 하는 시간 - 이보다 짧으면 다시 불러도 답이 오기 전에 끊긴다. */
    static final Duration MIN_RETRY_BUDGET = Duration.ofSeconds(1);

    /** 연결 수립 상한 - 전체 상한(10초)과 따로 두어 연결이 안 되는 날 재시도할 시간을 남긴다. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 사업자 안전 필터가 출력을 막았을 때의 finishReason 값들 - 번역 불가로 돌린다. */
    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY");

    /** 번역 문장 하나에 넉넉한 출력 토큰 상한 - 입력이 100자 이하라 대답이나 설명문을 길게 쓸 여지를 줄인다. */
    static final int MAX_OUTPUT_TOKENS = 512;

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final String systemInstruction;
    private final ObjectMapper objectMapper;
    private final Counter ok;
    private final Counter rateLimited;
    private final Counter error;

    GeminiDialectTranslator(String baseUrl, String apiKey, String model, Duration timeout, String systemInstruction,
                            ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT.compareTo(timeout) < 0 ? CONNECT_TIMEOUT : timeout)
                .build();
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.systemInstruction = systemInstruction;
        this.objectMapper = objectMapper;
        this.ok = counter(meterRegistry, "ok");
        this.rateLimited = counter(meterRegistry, "rate_limited");
        this.error = counter(meterRegistry, "error");
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder(ServiceMetrics.TRANSLATION_LLM_CALLS)
                .description("Gemini 호출 시도 - 태그 outcome은 ok | rate_limited | error (KAN-266)")
                .tag("outcome", outcome)
                .register(registry);
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public Reply translate(String text) {
        String body = requestBody(text);
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int attempt = 1; ; attempt++) {
            Duration remaining = Duration.ofNanos(deadline - System.nanoTime());
            if (remaining.isNegative() || remaining.isZero()) {
                throw new Unavailable("시간 상한 소진");
            }
            boolean canRetry = attempt == 1;
            HttpResponse<String> response;
            CompletableFuture<HttpResponse<String>> pending = httpClient.sendAsync(HttpRequest.newBuilder(endpoint)
                            .timeout(remaining)
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            try {
                response = pending.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException e) {
                // 시간 초과(본문 지연 포함)와 연결 실패 - 예외 메시지에는 URI만 있고 키와 본문은 없다. 끝나지 않은 교환은
                // 취소해 연결을 놓는다.
                pending.cancel(true);
                String reason = e instanceof ExecutionException && e.getCause() != null
                        ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName();
                error.increment();
                if (canRetry && retryBudgetLeft(deadline)) {
                    log.warn("Gemini 호출 실패 - 다시 부른다 attempt={} ({})", attempt, reason);
                    continue;
                }
                log.warn("Gemini 호출 실패 attempt={} ({})", attempt, reason);
                throw new Unavailable(reason);
            } catch (InterruptedException e) {
                pending.cancel(true);
                Thread.currentThread().interrupt();
                error.increment();
                throw new Unavailable("interrupted");
            }

            int status = response.statusCode();
            if (status == 200) {
                ok.increment();
                return parse(response.body());
            }
            if (status == 429) {
                rateLimited.increment();
                log.warn("Gemini 무료 한도 소진(429) - 다시 부르지 않는다");
                throw new Unavailable("429");
            }
            error.increment();
            if (status >= 500 && canRetry && retryBudgetLeft(deadline)) {
                log.warn("Gemini {} - 다시 부른다 attempt={}", status, attempt);
                continue;
            }
            // 400(요청 형식, 모델 이름), 403(키) 등은 다시 불러도 같다. 응답 본문은 남기지 않는다 - 입력을 되풀이할 수 있다.
            log.warn("Gemini 호출 실패 status={} attempt={}", status, attempt);
            throw new Unavailable("status " + status);
        }
    }

    private static boolean retryBudgetLeft(long deadline) {
        return deadline - System.nanoTime() >= MIN_RETRY_BUDGET.toNanos();
    }

    /**
     * 요청 본문. 사용자 입력은 JSON 문자열 값으로만 들어간다 - 따옴표와 줄바꿈이 이스케이프되므로 입력이 프롬프트의
     * 경계를 넘어 지시문처럼 읽힐 수 없다. 지시문은 {@code systemInstruction}에만 있다.
     */
    String requestBody(String text) {
        Map<String, Object> input = Map.of("text", text);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of(
                "status", Map.of("type", "STRING", "enum", List.of("TRANSLATED", "REJECTED")),
                "dialect", Map.of("type", "STRING")));
        schema.put("required", List.of("status", "dialect"));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", schema);
        // 번역은 창작이 아니다 - 같은 입력에 같은 답이 나오는 쪽으로 낮춘다.
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        request.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", objectMapper.writeValueAsString(input))))));
        request.put("generationConfig", generationConfig);
        return objectMapper.writeValueAsString(request);
    }

    /** 200 응답 읽기 - 안전 필터 차단, 거절, 번역, 형식 위반을 가른다. 본문은 로그에 남기지 않는다. */
    Reply parse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JacksonException e) {
            log.warn("Gemini 응답이 JSON이 아니다");
            return Reply.of(Kind.MALFORMED);
        }
        if (root.path("promptFeedback").path("blockReason").isString()) {
            return Reply.of(Kind.BLOCKED);
        }
        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            return Reply.of(Kind.MALFORMED);
        }
        String finishReason = candidate.path("finishReason").asString("");
        if (BLOCKED_FINISH_REASONS.contains(finishReason)) {
            return Reply.of(Kind.BLOCKED);
        }
        String text = answerText(candidate.path("content").path("parts"));
        if (text == null) {
            return Reply.of(Kind.MALFORMED);
        }
        JsonNode answer;
        try {
            answer = objectMapper.readTree(text);
        } catch (JacksonException e) {
            return Reply.of(Kind.MALFORMED);
        }
        String status = answer.path("status").asString("");
        if ("REJECTED".equals(status)) {
            return Reply.of(Kind.REFUSED);
        }
        JsonNode dialect = answer.path("dialect");
        if ("TRANSLATED".equals(status) && dialect.isString()) {
            return Reply.translated(dialect.asString());
        }
        return Reply.of(Kind.MALFORMED);
    }

    /** 답의 글 조각을 잇는다 - 생각 조각({@code thought: true})은 답이 아니라 뺀다. 글이 없으면 null. */
    private static @Nullable String answerText(JsonNode parts) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            JsonNode value = part.path("text");
            if (value.isString()) {
                text.append(value.asString());
            }
        }
        return text.isEmpty() ? null : text.toString();
    }
}
