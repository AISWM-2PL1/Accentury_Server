package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 번역 요청 1건의 기록 - prod 번역 기록 버킷의 JSON 객체 1개가 된다 (KAN-266, 명세서 §3.18).
 * <p>
 * {@code userId}는 기록 저장소가 대체 ID를 찾는 데만 쓰고 객체에는 싣지 않는다.
 *
 * @param input  사용자가 보낸 문장 (원문 그대로, 앞뒤 공백 포함)
 * @param output 응답으로 나간 사투리 문장. {@link TranslationResult#SUCCESS}가 아니면 null
 * @param llmMs  Gemini 호출에 걸린 시간(재시도 포함). 호출 전에 끝났으면 null
 * @param model  호출한(호출 전에 끝났으면 설정된) 모델 이름
 */
public record TranslationRecord(
        UUID requestId,
        Instant requestedAt,
        UUID userId,
        String input,
        @Nullable String output,
        TranslationResult result,
        long totalMs,
        @Nullable Long llmMs,
        String model) {

    /** 날짜 경계와 {@code requestedAt} 표기의 시간대 (2026-10-08 결정 - KST). */
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 객체 키 - {@code translations/yyyy/MM/dd/<requestId>.json} (날짜는 KST). */
    String key() {
        return "translations/" + requestedAt.atZone(ZONE).format(DATE_PATH) + "/" + requestId + ".json";
    }

    /** {@code requestedAt} 필드 값 - ISO 8601, KST 오프셋(+09:00)이다. */
    String requestedAtKst() {
        return requestedAt.atZone(ZONE).toOffsetDateTime().toString();
    }
}
