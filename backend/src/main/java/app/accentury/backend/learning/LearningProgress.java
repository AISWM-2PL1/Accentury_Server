package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 학습 진도와 홈 상태 (KAN-268, 명세서 §3.16, §3.17, §3.20).
 * <p>
 * <b>진도는 저장하지 않고 시도 기록에서 계산한다.</b> 억양 코스는 채점(§3.19)이 {@code COMPLETED}로 끝난 시도가 하나라도
 * 있는 카드를 완료로 치고(점수와 무관, 2026-10-11 결정), 모든 카드가 완료면 코스 완료다. 단어 세트는 완료한 시도가 하나라도
 * 있으면 완료이고 최근 정답률은 가장 최근에 완료한 시도의 것이다. 둘 다 현재 발행본의 기록만 센다 - 카드와 세트 id는 발행본
 * 안에서만 뜻이 있다. 그래서 완료를 저장하는 API가 없고, 완료 뒤 목록을 다시 조회하면 바로 완료로 보인다.
 * <p>
 * 추천 규칙은 {@link #recommend}에 있다. 기준 레벨은 계정의 최근 등급({@link LevelTestRecords})이다.
 */
@Service
public class LearningProgress {

    private final IntonationLearningRegistry intonationRegistry;
    private final WordLearningRegistry wordRegistry;
    private final IntonationAttemptRepository intonationAttempts;
    private final WordSetAttemptRepository wordAttempts;
    private final LevelTestRecords levelTestRecords;

    LearningProgress(IntonationLearningRegistry intonationRegistry, WordLearningRegistry wordRegistry,
                     IntonationAttemptRepository intonationAttempts, WordSetAttemptRepository wordAttempts,
                     LevelTestRecords levelTestRecords) {
        this.intonationRegistry = intonationRegistry;
        this.wordRegistry = wordRegistry;
        this.intonationAttempts = intonationAttempts;
        this.wordAttempts = wordAttempts;
        this.levelTestRecords = levelTestRecords;
    }

    /** 코스 목록 (A-1) - 완료 표시와 추천을 채운다. */
    IntonationCourseListResponse courses(UUID userId) {
        return courses(userId, baseRank(userId));
    }

    /** 세트 목록 (W-1) - 완료 표시, 최근 정답률, 추천을 채운다. */
    WordSetListResponse wordSets(UUID userId) {
        return wordSets(userId, baseRank(userId));
    }

    /** 홈 상태 (H-1) - 레벨테스트 완주 여부와 두 목록의 추천. */
    LearningHomeResponse home(UUID userId) {
        Optional<LevelTestRecords.Entry> record = levelTestRecords.find(userId);
        @Nullable Integer rank = record.map(LevelTestRecords.Entry::tierRank).orElse(null);
        String courseId = recommendedId(courses(userId, rank).courses(),
                IntonationCourseListResponse.Summary::recommended, IntonationCourseListResponse.Summary::courseId);
        String setId = recommendedId(wordSets(userId, rank).sets(), WordSetListResponse.Summary::recommended,
                WordSetListResponse.Summary::setId);
        return new LearningHomeResponse(record.isPresent(),
                record.map(LearningHomeResponse.LevelTest::from).orElse(null), courseId, setId);
    }

    private IntonationCourseListResponse courses(UUID userId, @Nullable Integer rank) {
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        Set<String> doneCards = new HashSet<>(
                intonationAttempts.findCompletedCardIds(userId, current.definition().contentVersion()));
        Map<String, Integer> doneByCourse = new HashMap<>();
        for (IntonationLearningDefinition.Course course : current.definition().courses()) {
            int done = (int) course.cards().stream().filter(card -> doneCards.contains(card.cardId())).count();
            doneByCourse.put(course.courseId(), done);
        }
        IntonationCourseListResponse unstarted = current.listResponse();
        IntonationCourseListResponse.Summary pick = recommend(unstarted.courses(),
                IntonationCourseListResponse.Summary::level,
                course -> doneByCourse.getOrDefault(course.courseId(), 0) == course.cardCount(), rank);
        return unstarted.withProgress(doneByCourse, pick == null ? null : pick.courseId());
    }

    private WordSetListResponse wordSets(UUID userId, @Nullable Integer rank) {
        WordLearningRegistry.Published current = wordRegistry.current();
        Map<String, Integer> latestAccuracy = new HashMap<>();
        for (WordSetAttempt attempt : wordAttempts.findCompletedNewestFirst(userId,
                current.definition().contentVersion())) {
            // 최근 완료가 앞이라 세트마다 처음 만난 시도가 최근 정답률이다.
            latestAccuracy.computeIfAbsent(attempt.setId(), setId -> WordAttemptResultResponse.accuracyPercent(
                    Objects.requireNonNull(attempt.correctCount(), "완료된 시도의 정답 수가 없다"),
                    attempt.itemCount()));
        }
        WordSetListResponse unstarted = current.listResponse();
        WordSetListResponse.Summary pick = recommend(unstarted.sets(), WordSetListResponse.Summary::level,
                set -> latestAccuracy.containsKey(set.setId()), rank);
        return unstarted.withProgress(latestAccuracy, pick == null ? null : pick.setId());
    }

    private @Nullable Integer baseRank(UUID userId) {
        return levelTestRecords.find(userId).map(LevelTestRecords.Entry::tierRank).orElse(null);
    }

    /**
     * 추천 1개 (§3.20, 2026-10-11 결정). 기준 레벨은 최근 등급의 순위(1~5, 학습 레벨과 같은 범위)이고 기록이 없으면 목록의
     * 가장 낮은 레벨이다. 기준 레벨 이상에서 완료하지 않은 항목 가운데 레벨이 가장 낮고, 같은 레벨 안에서는 목록 순서(seq)가
     * 가장 앞선 것을 고른다 - 기준 레벨을 다 끝냈으면 다음 레벨로 넘어간다. 기준 레벨보다 낮은 레벨은 고르지 않는다.
     *
     * @param ordered seq 오름차순 목록
     * @param rank    최근 등급 순위 - 기록이 없으면 null
     * @return 추천 항목 - 고를 것이 없으면 null
     */
    static <T> @Nullable T recommend(List<T> ordered, ToIntFunction<T> level, Predicate<T> completed,
                                     @Nullable Integer rank) {
        if (ordered.isEmpty()) {
            return null;
        }
        int base = rank != null ? rank : ordered.stream().mapToInt(level).min().orElseThrow();
        T pick = null;
        for (T item : ordered) {
            int itemLevel = level.applyAsInt(item);
            if (itemLevel < base || completed.test(item)) {
                continue;
            }
            // 같은 레벨이면 먼저 만난 것(seq가 앞선 것)을 유지한다.
            if (pick == null || itemLevel < level.applyAsInt(pick)) {
                pick = item;
            }
        }
        return pick;
    }

    private static <T> @Nullable String recommendedId(List<T> items, Predicate<T> recommended,
                                                      Function<T, String> id) {
        return items.stream().filter(recommended).map(id).findFirst().orElse(null);
    }
}
