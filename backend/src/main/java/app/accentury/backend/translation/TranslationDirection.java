package app.accentury.backend.translation;

import java.util.Optional;

/**
 * 번역 방향 (KAN-316, 명세서 §3.18) - 요청과 응답의 {@code direction} 값, 번역 기록의 {@code direction} 필드, 지표
 * {@code accentury.translation.requests}의 태그 {@code direction}이다. {@link #apiValue}는 정본이라 바꾸면 클라이언트와
 * 기록과 대시보드가 갈라진다.
 */
public enum TranslationDirection {

    /** 표준어 → 경남 사투리. 기본값 - 요청에 {@code direction}이 없으면 이것이다 (KAN-266 때부터의 유일한 방향). */
    TO_DIALECT("toDialect"),
    /** 경남 사투리 → 표준어 (2026-11-03 결정 - 경남 말을 듣고 뜻을 모르는 사용자의 학습 보조). */
    TO_STANDARD("toStandard");

    private final String apiValue;

    TranslationDirection(String apiValue) {
        this.apiValue = apiValue;
    }

    /** 요청과 응답, 기록, 지표에 쓰는 값 - {@code toDialect} | {@code toStandard}. */
    public String apiValue() {
        return apiValue;
    }

    /** 요청 값 읽기 - 대소문자까지 같아야 한다. 모르는 값은 비어 있고, 서비스가 400 {@code VALIDATION_FAILED}로 바꾼다. */
    static Optional<TranslationDirection> fromApiValue(String value) {
        for (TranslationDirection direction : values()) {
            if (direction.apiValue.equals(value)) {
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }
}
