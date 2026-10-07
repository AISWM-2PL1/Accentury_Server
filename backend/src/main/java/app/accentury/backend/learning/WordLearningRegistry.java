package app.accentury.backend.learning;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
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
import java.util.Optional;
import java.util.Set;

/**
 * 발행된 단어 학습 발행본의 저장소 (KAN-265, 명세서 §3.16).
 * <p>
 * {@code TestDefinitionRegistry}와 같은 구조다 - 발행 입력은 DB(마이그레이션의 INSERT)이고, 기동 시 전 행을 읽어
 * 검증하므로 유효하지 않은 발행본이 하나라도 있으면 서버가 뜨지 않는다. 로드 후 불변이라 잠금 없이 읽는다.
 * <p>
 * <b>활성 포인터가 없다.</b> 목록과 상세는 발행 시각이 가장 늦은 발행본({@link #current()})을 쓰고, 시도는 시작
 * 시점의 {@code contentVersion}에 고정되어 자기 발행본({@link #get})을 본다. 레벨테스트와 달리 2단계 롤아웃이
 * 필요 없는 것은 클라이언트가 발행본의 모양에 기대는 값(세트 수, 문항 수)을 전부 응답에서 읽기 때문이다.
 */
@Component
public class WordLearningRegistry {

    private static final Logger log = LoggerFactory.getLogger(WordLearningRegistry.class);

    static final String DIALECT_GYEONGNAM = "GYEONGNAM";
    static final int CHOICE_COUNT = 4;
    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 5;
    /** 식별자 상한 - 저장 컬럼({@code word_set_attempt.set_id}, {@code word_attempt_answer.item_id}/{@code choice_id})이 varchar(40)이다. */
    static final int MAX_ID_LENGTH = 40;

    /**
     * 발행본 하나와 거기서 미리 만든 공개 응답.
     *
     * @param definition   정답 포함 원본 - 채점의 정본
     * @param sets         setId로 찾는 세트
     * @param listResponse 세트 목록 응답 (§3.16) - 발행본이 불변이라 한 번 만든다
     * @param setResponses setId별 상세 응답 - 정답과 해설을 뺀 것
     */
    public record Published(WordLearningDefinition definition, Map<String, WordLearningDefinition.Set> sets,
                            WordSetListResponse listResponse, Map<String, WordSetResponse> setResponses) {

        /** 세트 - 없으면 404 {@code LEARNING_SET_NOT_FOUND}. */
        public WordLearningDefinition.Set set(String setId) {
            WordLearningDefinition.Set set = sets.get(setId);
            if (set == null) {
                throw new ApiException(ErrorCode.LEARNING_SET_NOT_FOUND);
            }
            return set;
        }

        public WordSetResponse setResponse(String setId) {
            WordSetResponse response = setResponses.get(setId);
            if (response == null) {
                throw new ApiException(ErrorCode.LEARNING_SET_NOT_FOUND);
            }
            return response;
        }
    }

    private final Map<String, Published> published = new HashMap<>();
    private final Published current;

    public WordLearningRegistry(ObjectMapper objectMapper, StoredWordLearningDefinitionRepository definitions) {
        Published latest = null;
        for (StoredWordLearningDefinition stored : definitions.findAllByOrderByPublishedAtAscContentVersionAsc()) {
            WordLearningDefinition definition = read(objectMapper, stored);
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
        require(latest != null, "발행된 단어 학습 발행본이 하나도 없다 - 마이그레이션이 적용되지 않았다");
        this.current = latest;
        log.info("단어 학습 발행본 {}종 발행 완료: {} (현재: {}, 세트 {}개, 카드 {}장)",
                published.size(), published.keySet(), current.definition().contentVersion(),
                current.sets().size(),
                current.definition().sets().stream().mapToInt(set -> set.cards().size()).sum());
    }

    /** 목록과 상세, 새 시도가 쓰는 발행본 - 발행 시각이 가장 늦은 것. */
    public Published current() {
        return current;
    }

    /** 시도가 고정한 버전의 발행본 - 없으면 데이터 오염이라 500이다 (발행본은 지워지지 않는다). */
    public Published get(String contentVersion) {
        return find(contentVersion).orElseThrow(() -> new IllegalStateException(
                "시도가 고정한 발행본(" + contentVersion + ")이 이 프로세스에 발행되어 있지 않다"));
    }

    public Optional<Published> find(String contentVersion) {
        return Optional.ofNullable(published.get(contentVersion));
    }

    private static Published publish(WordLearningDefinition definition) {
        List<WordLearningDefinition.Set> ordered = definition.sets().stream()
                .sorted(Comparator.comparingInt(WordLearningDefinition.Set::seq))
                .map(set -> new WordLearningDefinition.Set(set.setId(), set.seq(), set.level(), set.category(),
                        set.title(), List.copyOf(set.cards()),
                        set.items().stream().sorted(Comparator.comparingInt(WordLearningDefinition.Item::seq)).toList()))
                .toList();
        WordLearningDefinition sorted = new WordLearningDefinition(definition.contentVersion(), definition.dialect(),
                ordered);
        Map<String, WordLearningDefinition.Set> sets = new LinkedHashMap<>();
        Map<String, WordSetResponse> responses = new LinkedHashMap<>();
        List<WordSetListResponse.Summary> summaries = ordered.stream()
                .map(set -> new WordSetListResponse.Summary(set.setId(), set.seq(), set.level(), set.category(),
                        set.title(), set.cards().size(), set.items().size()))
                .toList();
        for (WordLearningDefinition.Set set : ordered) {
            sets.put(set.setId(), set);
            responses.put(set.setId(), WordSetResponse.from(sorted.contentVersion(), set));
        }
        return new Published(sorted, Map.copyOf(sets),
                new WordSetListResponse(sorted.contentVersion(), summaries), Map.copyOf(responses));
    }

    private static WordLearningDefinition read(ObjectMapper objectMapper, StoredWordLearningDefinition stored) {
        try {
            return objectMapper.readValue(stored.body(), WordLearningDefinition.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("단어 학습 발행본 본문을 읽을 수 없다: " + stored.contentVersion(), e);
        }
    }

    /**
     * 발행 전 검증 - 실패는 {@link IllegalStateException}, 서버 기동 중단.
     * <p>
     * 세트는 seq 1..n 연속, 레벨 1부터 5, 세트와 카드와 문항과 보기의 식별자는 발행본 안에서 유일하고 40자 이하다.
     * 카드 하나에 문항 하나(1:1)이고, 문항은 보기 4개 + 보기 안의 정답 + 해설이다. 정답표가 어긋난 발행본이
     * 뜨면 모든 제출이 틀린 채점을 받으므로 기동에서 막는다.
     */
    static void validate(WordLearningDefinition definition) {
        require(hasText(definition.contentVersion()), "contentVersion이 비어 있다");
        require(DIALECT_GYEONGNAM.equals(definition.dialect()),
                "MVP는 경남 발행본만 발행할 수 있다: dialect=" + definition.dialect());
        List<WordLearningDefinition.Set> sets = definition.sets();
        require(sets != null && !sets.isEmpty(), "세트가 하나도 없다: " + definition.contentVersion());

        Set<String> setIds = new HashSet<>();
        Set<Integer> seqs = new HashSet<>();
        Set<String> cardIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        for (WordLearningDefinition.Set set : sets) {
            String where = definition.contentVersion() + "/" + set.setId();
            requireId(set.setId(), "setId", where);
            require(setIds.add(set.setId()), "setId가 중복된다: " + where);
            require(seqs.add(set.seq()), "세트 seq가 중복된다: " + where);
            require(set.level() >= MIN_LEVEL && set.level() <= MAX_LEVEL, "레벨은 1부터 5다: " + where);
            require(hasText(set.category()) && hasText(set.title()), "분류와 제목이 비어 있다: " + where);
            require(set.cards() != null && !set.cards().isEmpty(), "카드가 없다: " + where);
            require(set.items() != null && set.items().size() == set.cards().size(),
                    "카드 하나에 문항 하나여야 한다: " + where);

            Set<String> localCardIds = new HashSet<>();
            for (WordLearningDefinition.Card card : set.cards()) {
                requireId(card.cardId(), "cardId", where);
                require(cardIds.add(card.cardId()), "cardId가 중복된다: " + where + "/" + card.cardId());
                localCardIds.add(card.cardId());
                require(hasText(card.standard()) && hasText(card.dialect()),
                        "카드의 표준어와 사투리가 비어 있다: " + where + "/" + card.cardId());
            }
            Set<String> usedCards = new HashSet<>();
            Set<Integer> itemSeqs = new HashSet<>();
            for (WordLearningDefinition.Item item : set.items()) {
                String at = where + "/" + item.itemId();
                requireId(item.itemId(), "itemId", where);
                require(itemIds.add(item.itemId()), "itemId가 중복된다: " + at);
                require(itemSeqs.add(item.seq()), "문항 seq가 중복된다: " + at);
                require(localCardIds.contains(item.cardId()), "문항이 이 세트에 없는 카드를 가리킨다: " + at);
                require(usedCards.add(item.cardId()), "한 카드에 문항이 둘이다: " + at);
                require(hasText(item.prompt()), "문항 문구가 비어 있다: " + at);
                require(hasText(item.explanation()), "해설이 비어 있다: " + at);
                require(item.choices() != null && item.choices().size() == CHOICE_COUNT,
                        "보기는 " + CHOICE_COUNT + "개여야 한다: " + at);
                Set<String> choiceIds = new HashSet<>();
                Set<String> texts = new HashSet<>();
                for (WordLearningDefinition.Choice choice : item.choices()) {
                    requireId(choice.choiceId(), "choiceId", at);
                    require(choiceIds.add(choice.choiceId()), "choiceId가 중복된다: " + at);
                    require(hasText(choice.text()) && texts.add(choice.text()), "보기 낱말이 비었거나 중복된다: " + at);
                }
                require(choiceIds.contains(item.correctChoiceId()), "정답이 보기 안에 없다: " + at);
            }
            for (int seq = 1; seq <= set.items().size(); seq++) {
                require(itemSeqs.contains(seq), "문항 seq가 1.." + set.items().size() + " 연속이 아니다: " + where);
            }
        }
        for (int seq = 1; seq <= sets.size(); seq++) {
            require(seqs.contains(seq), "세트 seq가 1.." + sets.size() + " 연속이 아니다: " + definition.contentVersion());
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
            throw new IllegalStateException("단어 학습 발행 거부 - " + message);
        }
    }
}
