package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.SsmPlaceholder;
import app.accentury.backend.learning.IntonationLearningRegistry;
import app.accentury.backend.learning.WordLearningRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 번역기 빈의 배선 (KAN-361, PR #77 리뷰) - 키가 없거나 비었거나 자리 표시 값이면 실제 빈 생성 경로가 「설정 안 됨」 번역기를
 * 돌려주는지 본다. 서비스 테스트는 {@link TranslationConfig#unconfigured(String)}을 직접 쓰므로 이 경로를 지나지 않는다.
 */
class TranslationConfigTest {

    private static AccenturyProperties.Translation translation(String apiKey) {
        return new AccenturyProperties.Translation(apiKey, AccenturyProperties.Translation.DEFAULT_MODEL,
                "https://generativelanguage.googleapis.com", Duration.ofSeconds(10), null, null, 6);
    }

    private static DialectTranslator bean(String apiKey) {
        AccenturyProperties properties = mock(AccenturyProperties.class);
        org.mockito.Mockito.when(properties.translation()).thenReturn(translation(apiKey));
        // 키가 없으면 지시문을 만들기 전에 끝나 학습 레지스트리는 쓰이지 않는다
        return new TranslationConfig().dialectTranslator(properties, mock(WordLearningRegistry.class),
                mock(IntonationLearningRegistry.class), JsonMapper.builder().build(), new SimpleMeterRegistry());
    }

    @Test
    void 키가_없거나_비었거나_자리_표시_값이면_설정_안_된_번역기다() {
        for (String key : new String[] {null, "", "   ", "\n", SsmPlaceholder.UNSET, SsmPlaceholder.UNSET + "\n"}) {
            DialectTranslator translator = bean(key);
            assertFalse(translator.configured(), "키 " + (key == null ? "null" : "\"" + key.strip() + "\""));
            DialectTranslator.Unavailable e = assertThrows(DialectTranslator.Unavailable.class,
                    () -> translator.translate("밥 먹었어?", TranslationDirection.TO_DIALECT));
            assertEquals(AccenturyProperties.Translation.DEFAULT_MODEL, translator.model());
            assertEquals("API 키 없음", e.getMessage());
        }
    }

    @Test
    void 키의_앞뒤_공백과_줄바꿈은_떼어_낸다() {
        assertEquals("abc", translation("abc\r\n").apiKey(), "SSM 값 끝의 개행이 헤더 예외로 500을 내지 않게 한다");
        assertEquals("abc", translation("  abc  ").apiKey());
        assertNull(translation(null).apiKey());
    }
}
