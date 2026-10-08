package app.accentury.backend.translation;

/**
 * 번역 요청 1건의 결과 종류 - 번역 기록의 {@code result} 필드이자 지표 {@code accentury.translation.requests}의 태그다
 * (명세서 §3.18). 이름은 정본이라 바꾸면 기록과 대시보드가 갈라진다.
 */
public enum TranslationResult {

    /** 번역 성공 - 200. */
    SUCCESS("success"),
    /** 번역 불가 - 서버 사전 검사, LLM 거절, 사업자 안전 필터, 출력 검사 탈락. 422 {@code TRANSLATION_REJECTED}. */
    REJECTED("rejected"),
    /** 길이 초과 - 422 {@code TRANSLATION_TOO_LONG}. */
    TOO_LONG("too_long"),
    /** LLM 호출 실패, 시간 초과, 무료 한도 소진(429) - 503 {@code TRANSLATION_UNAVAILABLE}. */
    LLM_FAILED("llm_failed");

    private final String metricTag;

    TranslationResult(String metricTag) {
        this.metricTag = metricTag;
    }

    String metricTag() {
        return metricTag;
    }
}
