package app.accentury.backend.learning;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.testdefinition.TestDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 발행된 억양 학습 발행본의 저장소 (KAN-264, 명세서 §3.17).
 * <p>
 * {@link WordLearningRegistry}와 같은 구조다 - 발행 입력은 DB(마이그레이션의 INSERT)이고, 기동 시 전 행을 읽어
 * 검증하므로 유효하지 않은 발행본이 하나라도 있으면 서버가 뜨지 않는다. 로드 후 불변이라 잠금 없이 읽는다.
 * 활성 포인터가 없고 목록과 상세는 발행 시각이 가장 늦은 발행본({@link #current()})을 쓴다.
 */
@Component
public class IntonationLearningRegistry {

    private static final Logger log = LoggerFactory.getLogger(IntonationLearningRegistry.class);

    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 5;
    /** 식별자 상한 - 단어 학습과 같다. 카드별 시도 기록(KAN-267)이 같은 폭의 열에 담는다. */
    static final int MAX_ID_LENGTH = 40;

    /**
     * 발행본 하나와 거기서 미리 만든 공개 응답.
     *
     * @param definition      scriptKey 포함 원본 - 채점(KAN-267)이 카드를 찾는 정본
     * @param listResponse    코스 목록 응답 (§3.17) - 발행본이 불변이라 한 번 만든다
     * @param courseResponses courseId별 상세 응답
     */
    public record Published(IntonationLearningDefinition definition, IntonationCourseListResponse listResponse,
                            Map<String, IntonationCourseResponse> courseResponses) {

        /** 코스 상세 - 없으면 404 {@code LEARNING_COURSE_NOT_FOUND}. */
        public IntonationCourseResponse courseResponse(String courseId) {
            IntonationCourseResponse response = courseResponses.get(courseId);
            if (response == null) {
                throw new ApiException(ErrorCode.LEARNING_COURSE_NOT_FOUND);
            }
            return response;
        }

        /**
         * 카드 하나 - 채점(KAN-267, §3.19)이 업로드 받은 {@code cardId}로 scriptKey를 찾는다. 없으면 404
         * {@code LEARNING_CARD_NOT_FOUND}. 발행본 카드는 백여 장이라 업로드마다 훑어도 된다.
         */
        public IntonationLearningDefinition.Card card(String cardId) {
            for (IntonationLearningDefinition.Course course : definition.courses()) {
                for (IntonationLearningDefinition.Card card : course.cards()) {
                    if (card.cardId().equals(cardId)) {
                        return card;
                    }
                }
            }
            throw new ApiException(ErrorCode.LEARNING_CARD_NOT_FOUND);
        }
    }

    private final Map<String, Published> published = new HashMap<>();
    private final Published current;

    public IntonationLearningRegistry(ObjectMapper objectMapper,
                                      StoredIntonationLearningDefinitionRepository definitions) {
        Published latest = null;
        for (StoredIntonationLearningDefinition stored : definitions.findAllByOrderByPublishedAtAscContentVersionAsc()) {
            IntonationLearningDefinition definition = read(objectMapper, stored);
            validate(definition);
            require(stored.contentVersion().equals(definition.contentVersion()),
                    "행의 content_version(" + stored.contentVersion() + ")과 본문의 contentVersion("
                            + definition.contentVersion() + ")이 다르다");
            require(stored.dialect().equals(definition.dialect()),
                    "행의 dialect(" + stored.dialect() + ")와 본문의 dialect(" + definition.dialect() + ")가 다르다: "
                            + stored.contentVersion());
            Published entry = publish(definition);
            published.put(definition.contentVersion(), entry);
            // 목록은 발행 시각 오름차순이라 마지막이 가장 늦은 발행본이다 (같은 시각이면 버전 문자열 순).
            latest = entry;
        }
        require(latest != null, "발행된 억양 학습 발행본이 하나도 없다 - 마이그레이션이 적용되지 않았다");
        this.current = latest;
        log.info("억양 학습 발행본 {}종 발행 완료: {} (현재: {}, 코스 {}개, 카드 {}장)",
                published.size(), published.keySet(), current.definition().contentVersion(),
                current.definition().courses().size(),
                current.definition().courses().stream().mapToInt(course -> course.cards().size()).sum());
    }

    /** 목록과 상세가 쓰는 발행본 - 발행 시각이 가장 늦은 것. */
    public Published current() {
        return current;
    }

    private static Published publish(IntonationLearningDefinition definition) {
        List<IntonationLearningDefinition.Course> ordered = definition.courses().stream()
                .sorted(Comparator.comparingInt(IntonationLearningDefinition.Course::seq))
                .map(course -> new IntonationLearningDefinition.Course(course.courseId(), course.seq(), course.level(),
                        course.topic(), course.title(), course.cards().stream()
                        .sorted(Comparator.comparingInt(IntonationLearningDefinition.Card::seq)).toList()))
                .toList();
        IntonationLearningDefinition sorted = new IntonationLearningDefinition(definition.contentVersion(),
                definition.dialect(), ordered);
        Map<String, IntonationCourseResponse> responses = new LinkedHashMap<>();
        for (IntonationLearningDefinition.Course course : ordered) {
            responses.put(course.courseId(), IntonationCourseResponse.from(sorted.contentVersion(), course));
        }
        List<IntonationCourseListResponse.Summary> summaries = ordered.stream()
                .map(course -> new IntonationCourseListResponse.Summary(course.courseId(), course.seq(),
                        course.level(), course.topic(), course.title(), course.cards().size()))
                .toList();
        return new Published(sorted, new IntonationCourseListResponse(sorted.contentVersion(), summaries),
                Map.copyOf(responses));
    }

    private static IntonationLearningDefinition read(ObjectMapper objectMapper,
                                                     StoredIntonationLearningDefinition stored) {
        try {
            return objectMapper.readValue(stored.body(), IntonationLearningDefinition.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("억양 학습 발행본 본문을 읽을 수 없다: " + stored.contentVersion(), e);
        }
    }

    /**
     * 발행 전 검증 - 실패는 {@link IllegalStateException}, 서버 기동 중단.
     * <p>
     * 코스는 seq 1..n 연속(코스가 없으면 빈 목록), 레벨 1부터 5, 코스와 카드의 식별자는 발행본 안에서 유일하고 40자
     * 이하다. 카드는 사투리 대사와 scriptKey(발행본 안에서 유일)와 가이드 곡선이 있어야 한다 - scriptKey가 없으면
     * 채점(KAN-263)이 기준 데이터를 찾지 못하고, 곡선이 망가지면 화면이 그리지 못한다. 기준 음원 경로는 아직 실을 수
     * 없다 - 경로와 합칠 환경별 기준 URL 설정이 없어서다 (§3.17).
     */
    static void validate(IntonationLearningDefinition definition) {
        require(hasText(definition.contentVersion()), "contentVersion이 비어 있다");
        require(WordLearningRegistry.DIALECT_GYEONGNAM.equals(definition.dialect()),
                "MVP는 경남 발행본만 발행할 수 있다: dialect=" + definition.dialect());
        List<IntonationLearningDefinition.Course> courses = definition.courses();
        require(courses != null, "courses가 없다: " + definition.contentVersion());

        Set<String> courseIds = new HashSet<>();
        Set<Integer> seqs = new HashSet<>();
        Set<String> cardIds = new HashSet<>();
        Set<String> scriptKeys = new HashSet<>();
        for (IntonationLearningDefinition.Course course : courses) {
            String where = definition.contentVersion() + "/" + course.courseId();
            requireId(course.courseId(), "courseId", where);
            require(courseIds.add(course.courseId()), "courseId가 중복된다: " + where);
            require(seqs.add(course.seq()), "코스 seq가 중복된다: " + where);
            require(course.level() >= MIN_LEVEL && course.level() <= MAX_LEVEL, "레벨은 1부터 5다: " + where);
            require(hasText(course.title()), "제목이 비어 있다: " + where);
            require(course.topic() == null || hasText(course.topic()), "주제가 있으면 비어 있지 않아야 한다: " + where);
            require(course.cards() != null && !course.cards().isEmpty(), "카드가 없다: " + where);

            Set<Integer> cardSeqs = new HashSet<>();
            for (IntonationLearningDefinition.Card card : course.cards()) {
                String at = where + "/" + card.cardId();
                requireId(card.cardId(), "cardId", where);
                require(cardIds.add(card.cardId()), "cardId가 중복된다: " + at);
                require(cardSeqs.add(card.seq()), "카드 seq가 중복된다: " + at);
                require(hasText(card.dialect()), "사투리 대사가 비어 있다: " + at);
                require(card.standard() == null || hasText(card.standard()),
                        "표준어 원문이 있으면 비어 있지 않아야 한다: " + at);
                require(hasText(card.scriptKey()), "scriptKey가 비어 있다: " + at);
                require(scriptKeys.add(card.scriptKey()), "scriptKey가 중복된다: " + at);
                validateGuideF0(card.guideF0(), at);
                require(card.referenceAudioPath() == null,
                        "기준 음원 경로를 실으려면 환경별 기준 URL 설정이 먼저 있어야 한다 (§3.17): " + at);
            }
            for (int seq = 1; seq <= course.cards().size(); seq++) {
                require(cardSeqs.contains(seq), "카드 seq가 1.." + course.cards().size() + " 연속이 아니다: " + where);
            }
        }
        for (int seq = 1; seq <= courses.size(); seq++) {
            require(seqs.contains(seq), "코스 seq가 1.." + courses.size() + " 연속이 아니다: " + definition.contentVersion());
        }
    }

    /** 테스트 정의 VOICE 문항의 곡선 검증과 같은 규칙 ({@code TestDefinitionRegistry}). */
    private static void validateGuideF0(TestDefinition.GuideF0 guideF0, String at) {
        require(guideF0 != null, "guideF0가 없다: " + at);
        require(hasText(guideF0.unit()), "guideF0.unit이 비어 있다: " + at);
        require(guideF0.frameIntervalMs() > 0, "guideF0.frameIntervalMs는 양수여야 한다: " + at);
        require(guideF0.values() != null && !guideF0.values().isEmpty(), "guideF0.values가 비어 있다: " + at);
        boolean hasLow = guideF0.bandLow() != null;
        boolean hasHigh = guideF0.bandHigh() != null;
        require(hasLow == hasHigh, "guideF0의 밴드는 상하한이 둘 다 있거나 둘 다 없어야 한다: " + at);
        if (hasLow) {
            require(guideF0.bandLow().size() == guideF0.values().size()
                            && guideF0.bandHigh().size() == guideF0.values().size(),
                    "guideF0 밴드의 길이가 values와 다르다: " + at);
        }
    }

    private static void requireId(String id, String name, String where) {
        require(hasText(id) && id.length() <= MAX_ID_LENGTH,
                name + "는 비어 있지 않고 " + MAX_ID_LENGTH + "자 이하여야 한다: " + where);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("억양 학습 발행 거부 - " + message);
        }
    }
}
