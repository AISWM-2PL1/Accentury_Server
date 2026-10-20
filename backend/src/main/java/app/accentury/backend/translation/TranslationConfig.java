package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.learning.IntonationLearningDefinition;
import app.accentury.backend.learning.IntonationLearningRegistry;
import app.accentury.backend.learning.WordLearningDefinition;
import app.accentury.backend.learning.WordLearningRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 번역 LLM 호출의 배선 (KAN-266).
 * <p>
 * 키가 없거나 자리 표시 값이면 호출하지 않는 번역기({@link #UNCONFIGURED})로 뜬다 - 번역만 503이고 기동과 다른 기능은
 * 영향이 없다 (키는 콘솔이 발급하는 값이라 apply 뒤에 넣는다, 후기 슬랙 URL과 같은 사정). 테스트는 이 빈을 가짜로 바꾼다.
 */
@Configuration(proxyBeanMethods = false)
class TranslationConfig {

    private static final Logger log = LoggerFactory.getLogger(TranslationConfig.class);

    /** 프롬프트에 싣는 참고 대사 수 - 어미와 말투의 예로 이만큼이면 충분하고, 늘리면 요청마다 토큰과 지연이 는다. */
    static final int REFERENCE_SENTENCES = 40;

    /** 키가 없는 배포의 번역기 - 부르면 언제나 503이다. */
    static DialectTranslator unconfigured(String model) {
        return new DialectTranslator() {
            @Override
            public Reply translate(String text) {
                throw new Unavailable("API 키 없음");
            }

            @Override
            public String model() {
                return model;
            }
        };
    }

    @Bean
    DialectTranslator dialectTranslator(AccenturyProperties properties, WordLearningRegistry words,
                                        IntonationLearningRegistry intonation, ObjectMapper objectMapper,
                                        MeterRegistry meterRegistry) {
        AccenturyProperties.Translation translation = properties.translation();
        if (!translation.apiKeyConfigured()) {
            log.warn("번역 API 키가 없다 - 번역은 전부 503 TRANSLATION_UNAVAILABLE (SSM ACCENTURY_TRANSLATION_APIKEY, KAN-266)");
            return unconfigured(translation.model());
        }
        String systemInstruction = TranslationHarness.systemInstruction(vocabulary(words), sentences(intonation));
        log.info("번역 LLM model={} timeout={} 참고 어휘와 대사로 지시문 {}자", translation.model(), translation.timeout(),
                systemInstruction.length());
        return new GeminiDialectTranslator(translation.baseUrl(), Objects.requireNonNull(translation.apiKey()),
                translation.model(), translation.timeout(), systemInstruction, objectMapper, meterRegistry);
    }

    /**
     * 참고 어휘 - 단어 학습 발행본의 카드(표준어 뜻과 사투리 낱말)다. 같은 낱말이 여러 세트에 있으면 한 번만 싣는다.
     * 발행본은 검수된 레벨테스트 어휘 풀이다 (KAN-276).
     */
    static List<TranslationHarness.VocabularyPair> vocabulary(WordLearningRegistry words) {
        Map<String, TranslationHarness.VocabularyPair> pairs = new LinkedHashMap<>();
        for (WordLearningDefinition.Set set : words.current().definition().sets()) {
            for (WordLearningDefinition.Card card : set.cards()) {
                pairs.putIfAbsent(card.dialect(),
                        new TranslationHarness.VocabularyPair(card.standard(), card.dialect()));
            }
        }
        return List.copyOf(pairs.values());
    }

    /** 참고 대사 - 억양 학습 발행본(검수된 레벨테스트 음성 풀)의 대사를 발행본 순서대로 {@value #REFERENCE_SENTENCES}개. */
    static List<String> sentences(IntonationLearningRegistry intonation) {
        return intonation.current().definition().courses().stream()
                .flatMap(course -> course.cards().stream())
                .map(IntonationLearningDefinition.Card::dialect)
                .limit(REFERENCE_SENTENCES)
                .toList();
    }
}
