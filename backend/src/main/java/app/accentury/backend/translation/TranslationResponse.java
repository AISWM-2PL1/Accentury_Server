package app.accentury.backend.translation;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * 번역 결과 - 문장 하나다 (명세서 §3.18, 2026-10-08 결정 - 후보 여러 개나 단어별 대응은 없다).
 * <p>
 * 결과 필드는 방향을 따른다 (KAN-316): {@code toDialect}면 {@code dialect}, {@code toStandard}면 {@code standard}이고
 * 다른 쪽은 싣지 않는다. {@code toDialect}의 {@code {dialect, remaining}} 모양은 KAN-266 때와 같고 {@code direction}이
 * 더해졌을 뿐이다.
 *
 * @param direction 요청의 방향 그대로 ({@link TranslationDirection#apiValue})
 * @param dialect   사투리 문장 ({@code toDialect}만)
 * @param standard  표준어 문장 ({@code toStandard}만)
 * @param remaining 이 번역 뒤 오늘 남은 성공 번역 수, 0부터 4 (2026-10-15 결정). 두 방향이 한 한도를 쓴다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TranslationResponse(String direction, @Nullable String dialect, @Nullable String standard, int remaining) {

    static TranslationResponse of(TranslationDirection direction, String output, int remaining) {
        return switch (direction) {
            case TO_DIALECT -> new TranslationResponse(direction.apiValue(), output, null, remaining);
            case TO_STANDARD -> new TranslationResponse(direction.apiValue(), null, output, remaining);
        };
    }
}
