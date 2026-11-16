package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
 * <b>세트 점수와 별도 같은 기록에서 계산한다 (KAN-359).</b> 억양 코스의 세트 점수는 카드별 최고 점수의 평균이고(카드별
 * 최고는 회차와 날짜가 달라도 합친다), 단어 세트의 세트 점수는 완료한 시도의 정답률 가운데 가장 높은 값이다. 최고 기록이라
 * 다시 풀어 점수가 내려가도 줄지 않는다. 별은 세트 점수의 구간이고, 구간(level)마다 별을 필요한 만큼 모으면 다음 구간이
 * 열린다({@link #levels}). 숫자와 규칙은 {@link LearningStars}에만 있고 여기는 기록을 모아 넘길 뿐이다. 발행본이 바뀌면
 * 별은 0부터다. 열림은 표시용이라 시도 시작을 막지 않는다.
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
    private final LearningAttendance attendance;

    LearningProgress(IntonationLearningRegistry intonationRegistry, WordLearningRegistry wordRegistry,
                     IntonationAttemptRepository intonationAttempts, WordSetAttemptRepository wordAttempts,
                     LevelTestRecords levelTestRecords, LearningAttendance attendance) {
        this.intonationRegistry = intonationRegistry;
        this.wordRegistry = wordRegistry;
        this.intonationAttempts = intonationAttempts;
        this.wordAttempts = wordAttempts;
        this.levelTestRecords = levelTestRecords;
        this.attendance = attendance;
    }

    /** 코스 목록 (A-1) - 완료 표시와 추천을 채운다. */
    IntonationCourseListResponse courses(UUID userId) {
        return courses(userId, baseRank(userId));
    }

    /** 세트 목록 (W-1) - 완료 표시, 최근 정답률, 추천을 채운다. */
    WordSetListResponse wordSets(UUID userId) {
        return wordSets(userId, baseRank(userId));
    }

    /** 홈 상태 (H-1) - 레벨테스트 완주 여부, 두 목록의 추천, 연속 출석. */
    LearningHomeResponse home(UUID userId) {
        Optional<LevelTestRecords.Entry> record = levelTestRecords.find(userId);
        @Nullable Integer rank = record.map(LevelTestRecords.Entry::tierRank).orElse(null);
        String courseId = recommendedId(courses(userId, rank).courses(),
                IntonationCourseListResponse.Summary::recommended, IntonationCourseListResponse.Summary::courseId);
        String setId = recommendedId(wordSets(userId, rank).sets(), WordSetListResponse.Summary::recommended,
                WordSetListResponse.Summary::setId);
        return new LearningHomeResponse(record.isPresent(),
                record.map(LearningHomeResponse.LevelTest::from).orElse(null), courseId, setId,
                attendance.streak(userId));
    }

    /**
     * 홈 앱바 달력 시트 (KAN-314, §3.20) - 출석 날짜({@link LearningAttendance#calendar})에 그날 마친 학습 항목을 붙인다.
     * <p>
     * 항목은 시도 기록에서 읽는다 - 출석 표에는 날짜만 남기기 때문이다 (V16 결정). 날짜는 각 완료 시각을 KST로 바꾼 날이라
     * 출석을 남길 때와 같은 기준이다. 단어 세트는 완료한 시도마다 1건(정답 수 / 문항 수), 억양 코스는 그날 채점을 마친
     * 카드를 코스로 묶어 1건(그날 카드 수 / 코스 카드 수), 레벨테스트는 최근 완주 1건만 남아 있어 그날에만 1건이다. 제목은
     * 시도가 고정한 발행본에서 찾고, 발행본이나 세트와 카드가 없으면(데이터 오염) 그 항목은 건너뛴다. 네 문장을 REPEATABLE
     * READ 읽기 트랜잭션 하나로 묶는다 - PostgreSQL은 이 격리에서 첫 문장의 스냅샷을 끝까지 쓰므로 출석과 시도가 같은
     * 시점을 본다 (기본 READ COMMITTED는 문장마다 새 스냅샷이다, PR #61 리뷰).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    MonthlyAttendanceResponse attendance(UUID userId, YearMonth month) {
        LearningAttendance.Calendar calendar = attendance.calendar(userId, month);
        Instant from = month.atDay(1).atStartOfDay(LearningAttendance.ZONE).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(LearningAttendance.ZONE).toInstant();

        Map<LocalDate, List<TimedItem>> itemsByDay = new LinkedHashMap<>();
        for (LocalDate day : calendar.days()) {
            itemsByDay.put(day, new ArrayList<>());
        }
        // 세트 - 완료한 시도마다 1건. 출석이 없는 날의 시도(기록 실패)는 붙일 자리가 없어 건너뛴다.
        for (WordSetAttempt attempt : wordAttempts.findCompletedBetween(userId, from, to)) {
            Instant completedAt = Objects.requireNonNull(attempt.completedAt());
            List<TimedItem> items = itemsByDay.get(LearningAttendance.attendanceDate(completedAt));
            if (items == null) {
                continue;
            }
            wordRegistry.find(attempt.contentVersion())
                    .map(published -> published.sets().get(attempt.setId()))
                    .ifPresent(set -> items.add(new TimedItem(completedAt, new MonthlyAttendanceResponse.Item(
                            MonthlyAttendanceResponse.Kind.SET, attempt.id(), set.setId(), set.title(),
                            Objects.requireNonNullElse(attempt.correctCount(), 0), attempt.itemCount()))));
        }
        // 코스 - 그날 채점을 마친 카드를 (발행본, 코스)로 묶는다. 코스와 카드 id는 발행본마다 매기므로 발행본이 다르면 다른
        // 코스다 (Codex 리뷰). 같은 카드를 두 번 마쳐도 한 장이고, 항목의 시각은 그날 마지막으로 마친 카드의 시각이다.
        Map<LocalDate, Map<CourseKey, CourseProgress>> coursesByDay = new LinkedHashMap<>();
        for (IntonationAttempt attempt : intonationAttempts.findCompletedBetween(userId, from, to)) {
            Instant finishedAt = Objects.requireNonNull(attempt.finishedAt());
            LocalDate day = LearningAttendance.attendanceDate(finishedAt);
            if (!itemsByDay.containsKey(day)) {
                continue;
            }
            intonationRegistry.find(attempt.contentVersion())
                    .flatMap(published -> published.courseOf(attempt.cardId()))
                    .ifPresent(course -> coursesByDay.computeIfAbsent(day, d -> new LinkedHashMap<>())
                            .computeIfAbsent(new CourseKey(attempt.contentVersion(), course.courseId()),
                                    key -> new CourseProgress(course))
                            .add(attempt.cardId(), finishedAt));
        }
        coursesByDay.forEach((day, byCourse) -> byCourse.forEach((key, progress) -> itemsByDay.get(day)
                .add(new TimedItem(progress.lastFinishedAt, new MonthlyAttendanceResponse.Item(
                        MonthlyAttendanceResponse.Kind.COURSE, key.contentVersion() + ":" + key.courseId(),
                        progress.course.courseId(), progress.course.title(), progress.cards.size(),
                        progress.course.cards().size())))));
        // 레벨테스트 - 최근 완주 1건만 남는다 (§3.20). 그날이 이 달이면 붙인다.
        levelTestRecords.find(userId).ifPresent(entry -> {
            List<TimedItem> items = itemsByDay.get(LearningAttendance.attendanceDate(entry.completedAt()));
            if (items != null) {
                items.add(new TimedItem(entry.completedAt(), new MonthlyAttendanceResponse.Item(
                        MonthlyAttendanceResponse.Kind.LEVEL_TEST, entry.testVersion(), entry.testVersion(),
                        entry.tierName(), 1, 1)));
            }
        });

        List<MonthlyAttendanceResponse.Day> days = new ArrayList<>();
        itemsByDay.forEach((day, items) -> days.add(new MonthlyAttendanceResponse.Day(day,
                items.stream().sorted(Comparator.comparing(TimedItem::at)).map(TimedItem::item).toList())));
        return new MonthlyAttendanceResponse(month.getYear(), month.getMonthValue(), days, days.size(),
                calendar.lastMonthCount(), calendar.streak(), calendar.today());
    }

    /** 하루 안의 항목은 마친 시각 순으로 늘어놓는다 - 종류별로 모았다가 마지막에 섞어 정렬한다. */
    private record TimedItem(Instant at, MonthlyAttendanceResponse.Item item) {
    }

    /** 코스는 발행본 안에서만 유일하다. */
    private record CourseKey(String contentVersion, String courseId) {
    }

    /** 하루에 한 코스에서 마친 카드와 마지막으로 마친 시각. */
    private static final class CourseProgress {
        private final IntonationLearningDefinition.Course course;
        private final Set<String> cards = new HashSet<>();
        private Instant lastFinishedAt = Instant.MIN;

        private CourseProgress(IntonationLearningDefinition.Course course) {
            this.course = course;
        }

        private void add(String cardId, Instant finishedAt) {
            cards.add(cardId);
            if (finishedAt.isAfter(lastFinishedAt)) {
                lastFinishedAt = finishedAt;
            }
        }
    }

    /**
     * 구간 요약 (KAN-359, §3.20) - 구간(level)마다 세트 수, 완료 세트 수, 모은 별, 필요 별, 열림과 전체 별 합계.
     * <p>
     * 두 목록({@link #courses}, {@link #wordSets})을 그대로 만들어 구간으로 접는다 - 같은 계산 경로라 요약과 목록의 값이
     * 어긋나지 않는다. 추천은 쓰지 않으므로 등급을 읽지 않는다 (열림은 별만 본다, 등급과 무관). 억양 시도와 단어 시도를
     * REPEATABLE READ 읽기 트랜잭션 하나로 묶어 같은 스냅샷에서 센다 ({@link #attendance}와 같은 이유).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    LearningLevelsResponse levels(UUID userId) {
        Map<Integer, LevelTally> byLevel = new TreeMap<>();
        for (IntonationCourseListResponse.Summary course : courses(userId, null).courses()) {
            byLevel.computeIfAbsent(course.level(), level -> new LevelTally()).add(course.completed(), course.stars());
        }
        for (WordSetListResponse.Summary set : wordSets(userId, null).sets()) {
            byLevel.computeIfAbsent(set.level(), level -> new LevelTally()).add(set.completed(), set.stars());
        }
        List<LearningLevelsResponse.Level> levels = new ArrayList<>();
        int totalStars = 0;
        // 가장 낮은 구간은 늘 열려 있고, 그 밖은 바로 앞 구간의 별로 정한다.
        boolean unlocked = true;
        for (Map.Entry<Integer, LevelTally> entry : byLevel.entrySet()) {
            LevelTally tally = entry.getValue();
            levels.add(new LearningLevelsResponse.Level(entry.getKey(), tally.setCount, tally.completedSetCount,
                    tally.stars, LearningStars.requiredStars(tally.setCount), unlocked));
            totalStars += tally.stars;
            unlocked = LearningStars.unlocksNext(tally.stars, tally.setCount);
        }
        return new LearningLevelsResponse(totalStars, LearningStars.THRESHOLDS, List.copyOf(levels));
    }

    /** 구간 하나의 합계 - 억양 코스와 단어 세트를 함께 센다. */
    private static final class LevelTally {
        private int setCount;
        private int completedSetCount;
        private int stars;

        private void add(boolean completed, int setStars) {
            setCount++;
            if (completed) {
                completedSetCount++;
            }
            stars += setStars;
        }
    }

    private IntonationCourseListResponse courses(UUID userId, @Nullable Integer rank) {
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        // 채점을 마친 시도마다 한 행 - 행이 있는 카드가 완료이고(점수와 무관), 카드별 최고 점수가 세트 점수의 재료다.
        Set<String> doneCards = new HashSet<>();
        Map<String, Integer> bestByCard = new HashMap<>();
        for (IntonationAttemptRepository.CardScore row : intonationAttempts.findCompletedCardScores(userId,
                current.definition().contentVersion())) {
            doneCards.add(row.getCardId());
            Integer score = row.getScore();
            if (score != null) {
                bestByCard.put(row.getCardId(), LearningStars.best(bestByCard.get(row.getCardId()), score));
            }
        }
        Map<String, Integer> doneByCourse = new HashMap<>();
        Map<String, Integer> scoreByCourse = new HashMap<>();
        for (IntonationLearningDefinition.Course course : current.definition().courses()) {
            List<String> cardIds = course.cards().stream().map(IntonationLearningDefinition.Card::cardId).toList();
            doneByCourse.put(course.courseId(), (int) cardIds.stream().filter(doneCards::contains).count());
            Integer score = LearningStars.intonationSetScore(cardIds, bestByCard);
            if (score != null) {
                scoreByCourse.put(course.courseId(), score);
            }
        }
        IntonationCourseListResponse unstarted = current.listResponse();
        IntonationCourseListResponse.Summary pick = recommend(unstarted.courses(),
                IntonationCourseListResponse.Summary::level,
                course -> doneByCourse.getOrDefault(course.courseId(), 0) == course.cardCount(), rank);
        return unstarted.withProgress(doneByCourse, scoreByCourse, pick == null ? null : pick.courseId());
    }

    private WordSetListResponse wordSets(UUID userId, @Nullable Integer rank) {
        WordLearningRegistry.Published current = wordRegistry.current();
        Map<String, Integer> latestAccuracy = new HashMap<>();
        Map<String, Integer> bestAccuracy = new HashMap<>();
        for (WordSetAttempt attempt : wordAttempts.findCompletedNewestFirst(userId,
                current.definition().contentVersion())) {
            int accuracy = WordAttemptResultResponse.accuracyPercent(
                    Objects.requireNonNull(attempt.correctCount(), "완료된 시도의 정답 수가 없다"),
                    attempt.itemCount());
            // 최근 완료가 앞이라 세트마다 처음 만난 시도가 최근 정답률이다. 세트 점수는 그와 달리 전 시도의 최고 값이다.
            latestAccuracy.putIfAbsent(attempt.setId(), accuracy);
            bestAccuracy.put(attempt.setId(), LearningStars.best(bestAccuracy.get(attempt.setId()), accuracy));
        }
        WordSetListResponse unstarted = current.listResponse();
        WordSetListResponse.Summary pick = recommend(unstarted.sets(), WordSetListResponse.Summary::level,
                set -> latestAccuracy.containsKey(set.setId()), rank);
        return unstarted.withProgress(latestAccuracy, bestAccuracy, pick == null ? null : pick.setId());
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
