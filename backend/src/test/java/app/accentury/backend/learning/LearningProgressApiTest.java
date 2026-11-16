package app.accentury.backend.learning;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.SessionTestFlow;
import app.accentury.backend.SessionTestFlow.SessionHandle;
import app.accentury.backend.analysis.AnalysisJobRepository;
import app.accentury.backend.analysis.AnalysisJobTransitions;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.result.TestResult;
import app.accentury.backend.result.TestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학습 진도와 홈 상태의 실행 가능한 명세 (KAN-268, 명세서 §3.16, §3.17, §3.20) - 실제 PostgreSQL과 Redis 위에서 가짜 IdP로
 * 돈다.
 * <p>
 * 코스와 세트는 발행본({@link IntonationLearningRegistry}, {@link WordLearningRegistry})에서 읽는다 - id를 리터럴로 박지 않는
 * 것은 발행본이 재발행되면 테스트가 조용히 옛 구성을 검사하게 되기 때문이다. 억양 채점 시도는 AI를 거치지 않도록 표에 직접
 * 심는다 (채점 경로 자체는 {@code IntonationScoringApiTest}가 본다).
 */
@AutoConfigureMockMvc
@Import(RedisTestcontainer.class)
@TestPropertySource(properties = "accentury.auth.fake-idp=true")
class LearningProgressApiTest extends IntegrationTest {

    private static final String POLICY_VERSION = AccenturyProperties.Auth.PRIVACY_POLICY_VERSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntonationLearningRegistry intonationRegistry;

    @Autowired
    private WordLearningRegistry wordRegistry;

    @Autowired
    private LevelTestRecords levelTestRecords;

    @Autowired
    private LearningAttendance attendance;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private AnalysisJobTransitions transitions;

    @Autowired
    private TestResultRepository resultRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcClient jdbcClient;

    private SessionTestFlow flow;

    @BeforeEach
    void setUp() {
        flow = new SessionTestFlow(mockMvc, objectMapper, analysisJobRepository, transitions);
    }

    // === 인증 ===

    @Test
    void 토큰이_없으면_홈은_401이다() throws Exception {
        mockMvc.perform(get("/v0/learning/home")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    // === 홈 상태 (AC 1) ===

    @Test
    void 홈은_테스트_전과_후가_다르고_세션과_결과가_지워진_뒤에도_후로_남는다() throws Exception {
        Account account = signUp();

        JsonNode before = home(account);
        assertFalse(before.get("levelTestTaken").asBoolean());
        assertTrue(before.get("levelTest").isNull());

        SessionHandle session = completeLevelTest(account);
        TestResult result = resultRepository.findBySessionId(session.id()).orElseThrow();

        JsonNode after = home(account);
        assertTrue(after.get("levelTestTaken").asBoolean());
        JsonNode levelTest = after.get("levelTest");
        assertEquals(result.testVersion(), levelTest.get("testVersion").asString());
        assertEquals(result.scoreVersion(), levelTest.get("scoreVersion").asString());
        assertEquals(result.tierCode(), levelTest.get("tier").get("code").asString());
        assertEquals(result.tierName(), levelTest.get("tier").get("name").asString());
        assertEquals(result.tierRank(), levelTest.get("tier").get("rank").asInt());
        assertEquals(result.tierCount(), levelTest.get("tier").get("of").asInt());
        assertFalse(levelTest.has("overall"), "점수는 남기지 않는다");

        // 24시간 뒤 정리 작업이 세션과 결과를 행째 지운 상태를 만든다 (§5.5) - 판정은 그대로 후다.
        jdbc.update("delete from test_session where id = ?", session.id());
        assertTrue(resultRepository.findBySessionId(session.id()).isEmpty());
        JsonNode later = home(account);
        assertTrue(later.get("levelTestTaken").asBoolean());
        assertEquals(result.tierRank(), later.get("levelTest").get("tier").get("rank").asInt());

        // 익명 세션의 완주는 어느 계정에도 남지 않는다.
        int recordsBefore = count("select count(*) from level_test_record");
        SessionHandle anonymous = flow.createSession();
        flow.answerVocab(anonymous);
        flow.completeVoice(anonymous);
        flow.complete(anonymous, "anon");
        assertEquals(recordsBefore, count("select count(*) from level_test_record"));
    }

    @Test
    void 최근_등급은_더_늦은_완주만_덮어쓰고_탈퇴한_계정에는_쓰지_않는다() throws Exception {
        Account account = signUp();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertTrue(levelTestRecords.record(account.userId(), entry(3, now)));
        assertFalse(levelTestRecords.record(account.userId(), entry(5, now.minusSeconds(60))), "더 이른 완주는 덮지 않는다");
        assertEquals(3, levelTestRecords.find(account.userId()).orElseThrow().tierRank());
        assertTrue(levelTestRecords.record(account.userId(), entry(2, now.plusSeconds(60))));
        assertEquals(2, levelTestRecords.find(account.userId()).orElseThrow().tierRank());

        Account withdrawn = signUp();
        authed(post("/v0/users/me/withdrawal"), withdrawn.access()).andExpect(status().isNoContent());
        assertFalse(levelTestRecords.record(withdrawn.userId(), entry(4, now)));
        assertTrue(levelTestRecords.find(withdrawn.userId()).isEmpty());
    }

    // === 연속 출석 (KAN-286) ===

    @Test
    void 레벨테스트_완주와_학습_완료가_그날_출석을_한_번만_남기고_홈에_보인다() throws Exception {
        Account account = signUp();
        JsonNode before = home(account).get("streak");
        assertEquals(0, before.get("currentDays").asInt());
        assertFalse(before.get("attendedToday").asBoolean());
        assertTrue(before.get("lastAttendedDate").isNull());

        LocalDate today = today();
        completeLevelTest(account);
        List<LocalDate> afterTest = attendedDates(account.userId());
        // 같은 날 세트를 두 번 끝내도 1행이다.
        WordLearningDefinition.Set set = wordRegistry.current().definition().sets().get(0);
        solve(account, set, 0);
        solve(account, set, 1);
        List<LocalDate> afterSets = attendedDates(account.userId());
        JsonNode streak = home(account).get("streak");
        assumeSameDay(today);
        assertEquals(List.of(today), afterTest, "레벨테스트 완주는 출석이다");
        assertEquals(List.of(today), afterSets);
        assertEquals(1, streak.get("currentDays").asInt());
        assertTrue(streak.get("attendedToday").asBoolean());
        assertEquals(today.toString(), streak.get("lastAttendedDate").asString());

        // 단어 세트 완료만으로도 출석이다. 시작만 한 시도는 출석이 아니다.
        Account learner = signUp();
        authed(post("/v0/learning/word-sets/" + set.setId() + "/attempts"), learner.access())
                .andExpect(status().isCreated());
        assertEquals(List.of(), attendedDates(learner.userId()), "시작만 하면 출석이 아니다");
        today = today();
        solve(learner, set, 0);
        List<LocalDate> learned = attendedDates(learner.userId());
        assumeSameDay(today);
        assertEquals(List.of(today), learned);
    }

    @Test
    void 연속_출석은_오늘_아직이면_어제까지를_세고_하루를_건너뛰면_0이다() throws Exception {
        LocalDate today = today();

        // 어제와 그제 출석, 오늘은 아직 - 오늘이 끝나기 전이라 끊긴 것으로 보지 않는다.
        Account account = signUp();
        seedAttendance(account.userId(), today.minusDays(1), today.minusDays(2), today.minusDays(4));
        JsonNode pending = home(account).get("streak");
        // 오늘 출석하면 오늘까지 이어진다.
        assertTrue(attendance.record(account.userId(), Instant.now()));
        JsonNode attended = home(account).get("streak");
        // 어제를 건너뛰었으면 0이다 - 마지막 출석일은 그대로 보인다.
        Account lapsed = signUp();
        seedAttendance(lapsed.userId(), today.minusDays(2), today.minusDays(3));
        JsonNode broken = home(lapsed).get("streak");
        assumeSameDay(today);

        assertEquals(2, pending.get("currentDays").asInt());
        assertFalse(pending.get("attendedToday").asBoolean());
        assertEquals(today.minusDays(1).toString(), pending.get("lastAttendedDate").asString());
        assertEquals(3, attended.get("currentDays").asInt());
        assertTrue(attended.get("attendedToday").asBoolean());
        assertEquals(0, broken.get("currentDays").asInt());
        assertFalse(broken.get("attendedToday").asBoolean());
        assertEquals(today.minusDays(2).toString(), broken.get("lastAttendedDate").asString());
    }

    @Test
    void 출석_기록이_실패해도_완료_응답은_그대로다() throws Exception {
        Account account = signUp();
        WordLearningDefinition.Set set = wordRegistry.current().definition().sets().get(0);
        // 출석 넣기만 실패하게 한다 - 기존 행은 검사하지 않고(not valid) 새 행은 전부 거부한다.
        jdbc.execute("alter table learning_attendance add constraint ck_test_attendance_fails check (false) not valid");
        try {
            completeLevelTest(account);   // 200 READY를 단언한다
            solve(account, set, 0);       // 완료 200을 단언한다
        } finally {
            jdbc.execute("alter table learning_attendance drop constraint ck_test_attendance_fails");
        }
        assertEquals(List.of(), attendedDates(account.userId()));
        assertTrue(home(account).get("levelTestTaken").asBoolean(), "결과와 최근 등급은 커밋됐다");
        assertTrue(find(sets(account).get("sets"), "setId", set.setId()).get("completed").asBoolean(),
                "세트 완료는 커밋됐다");
    }

    @Test
    void 탈퇴한_계정에는_출석을_쓰지_않는다() throws Exception {
        Account withdrawn = signUp();
        authed(post("/v0/users/me/withdrawal"), withdrawn.access()).andExpect(status().isNoContent());
        assertFalse(attendance.record(withdrawn.userId(), Instant.now()));
        assertEquals(List.of(), attendedDates(withdrawn.userId()));
    }

    // === 월별 출석 - 홈 달력 시트 (KAN-314) ===

    @Test
    void 월별_출석은_그_달의_출석_날짜와_이번달과_지난달_수와_홈과_같은_연속_출석과_오늘을_준다() throws Exception {
        Account account = signUp();
        LocalDate today = today();
        // 지난달과 그 전달에 걸쳐 심는다 - 요청한 달만 나오고 지난달은 수로만 나온다. 날짜는 전부 과거라 오늘 판정과 무관하다.
        YearMonth target = YearMonth.from(today).minusMonths(1);
        YearMonth before = target.minusMonths(1);
        seedAttendance(account.userId(), target.atDay(1), target.atDay(2), target.atDay(3), target.atDay(5),
                target.atDay(8), before.atDay(20), before.atEndOfMonth());
        JsonNode month = attendanceMonth(account, target);
        assertEquals(target.getYear(), month.get("year").asInt());
        assertEquals(target.getMonthValue(), month.get("month").asInt());
        assertEquals(List.of(target.atDay(1), target.atDay(2), target.atDay(3), target.atDay(5), target.atDay(8)),
                dates(month.get("days")));
        assertEquals(5, month.get("monthCount").asInt());
        assertEquals(2, month.get("lastMonthCount").asInt());
        for (JsonNode day : month.get("days")) {
            assertEquals(0, day.get("items").size(), "심은 출석에는 시도 기록이 없다");
        }
        assumeSameDay(today);
        assertEquals(today.toString(), month.get("today").asString());

        // 연속 출석은 홈과 같은 값이다.
        seedAttendance(account.userId(), today, today.minusDays(1));
        JsonNode streak = attendanceMonth(account, YearMonth.from(today)).get("streak");
        JsonNode homeStreak = home(account).get("streak");
        assumeSameDay(today);
        assertEquals(2, streak.get("currentDays").asInt());
        assertTrue(streak.get("attendedToday").asBoolean());
        assertEquals(today.toString(), streak.get("lastAttendedDate").asString());
        assertEquals(homeStreak, streak);
    }

    @Test
    void 출석한_날에는_그날_마친_세트와_코스와_레벨테스트가_붙는다() throws Exception {
        Account account = signUp();
        LocalDate today = today();
        // 레벨테스트 완주, 세트 하나를 1문항 틀리고 완료, 첫 코스의 카드 두 장 채점 완료(한 장은 두 번, 실패 한 건은 제외)
        // 순서로 만든다 - 응답은 종류가 아니라 마친 시각 순이어야 한다 (Codex 리뷰).
        completeLevelTest(account);
        WordLearningDefinition.Set set = wordRegistry.current().definition().sets().get(0);
        solve(account, set, 1);
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        IntonationLearningDefinition.Course course = current.definition().courses().get(0);
        String version = current.definition().contentVersion();
        seedAttempt(account.userId(), version, course.cards().get(0).cardId(), "COMPLETED");
        seedAttempt(account.userId(), version, course.cards().get(0).cardId(), "COMPLETED");
        seedAttempt(account.userId(), version, course.cards().get(1).cardId(), "COMPLETED");
        seedAttempt(account.userId(), version, course.cards().get(1).cardId(), "FAILED");

        JsonNode month = attendanceMonth(account, YearMonth.from(today));
        assumeSameDay(today);
        assertEquals(List.of(today), dates(month.get("days")));
        JsonNode items = month.get("days").get(0).get("items");
        Map<String, JsonNode> byKind = new LinkedHashMap<>();
        for (JsonNode item : items) {
            byKind.put(item.get("kind").asString(), item);
        }
        assertEquals(3, items.size(), items.toString());
        assertEquals(List.of("LEVEL_TEST", "SET", "COURSE"), new ArrayList<>(byKind.keySet()), "마친 시각 순이다");
        JsonNode setItem = byKind.get("SET");
        assertEquals(jdbc.queryForObject("select id from word_set_attempt where user_id = ? and set_id = ?",
                String.class, account.userId(), set.setId()), setItem.get("key").asString(), "세트 키는 시도 id다");
        assertEquals(set.setId(), setItem.get("id").asString());
        assertEquals(set.title(), setItem.get("title").asString());
        assertEquals(set.items().size() - 1, setItem.get("done").asInt());
        assertEquals(set.items().size(), setItem.get("total").asInt());
        JsonNode courseItem = byKind.get("COURSE");
        assertEquals(version + ":" + course.courseId(), courseItem.get("key").asString());
        assertEquals(course.courseId(), courseItem.get("id").asString());
        assertEquals(course.title(), courseItem.get("title").asString());
        assertEquals(2, courseItem.get("done").asInt(), "같은 카드 두 번은 한 장, 실패는 세지 않는다");
        assertEquals(course.cards().size(), courseItem.get("total").asInt());
        JsonNode levelTest = byKind.get("LEVEL_TEST");
        JsonNode home = home(account).get("levelTest");
        assertEquals(home.get("testVersion").asString(), levelTest.get("key").asString());
        assertEquals(home.get("testVersion").asString(), levelTest.get("id").asString());
        assertEquals(home.get("tier").get("name").asString(), levelTest.get("title").asString());
        assertEquals(1, levelTest.get("done").asInt());
        assertEquals(1, levelTest.get("total").asInt());
    }

    @Test
    void 달력의_오늘과_연속_출석은_같은_기준일이고_달_범위만_나온다() throws Exception {
        // 고정 시계라 자정 경계를 건너뛰지 않고 본다 (PR #61 리뷰). 10-31 23:59:59 KST와 11-01 00:00:00 KST.
        Account account = signUp();
        seedAttendance(account.userId(), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31));
        LearningAttendance beforeMidnight = new LearningAttendance(jdbcClient,
                Clock.fixed(Instant.parse("2026-10-31T14:59:59Z"), ZoneOffset.UTC));
        LearningAttendance afterMidnight = new LearningAttendance(jdbcClient,
                Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC));

        LearningAttendance.Calendar october = beforeMidnight.calendar(account.userId(), YearMonth.of(2026, 10));
        assertEquals(LocalDate.of(2026, 10, 31), october.today());
        assertEquals(List.of(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)), october.days());
        assertEquals(0, october.lastMonthCount());
        assertEquals(new LearningAttendance.Streak(2, true, LocalDate.of(2026, 10, 31)), october.streak());

        LearningAttendance.Calendar november = afterMidnight.calendar(account.userId(), YearMonth.of(2026, 11));
        assertEquals(LocalDate.of(2026, 11, 1), november.today());
        assertEquals(List.of(), november.days(), "미래 달과 아직 출석이 없는 달은 비어 있다");
        assertEquals(2, november.lastMonthCount());
        assertEquals(new LearningAttendance.Streak(2, false, LocalDate.of(2026, 10, 31)), november.streak(),
                "자정이 지나면 아직 출석 전이지만 어제까지 이어진 2일이다");
        assertEquals(november.streak(), afterMidnight.streak(account.userId()), "홈과 같은 값이다");

        // 이틀 뒤에는 끊긴다 - 마지막 출석일은 그대로 보인다.
        LearningAttendance twoDaysLater = new LearningAttendance(jdbcClient,
                Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneOffset.UTC));
        assertEquals(new LearningAttendance.Streak(0, false, LocalDate.of(2026, 10, 31)),
                twoDaysLater.calendar(account.userId(), YearMonth.of(2026, 11)).streak());
        assertEquals(LearningAttendance.Streak.NONE, twoDaysLater.calendar(signUp().userId(), YearMonth.of(2026, 11)).streak());
    }

    @Test
    void 월_경계는_KST_자정이다() throws Exception {
        Account account = signUp();
        // 2026-09-30 23:59 KST와 2026-10-01 00:00 KST - UTC로는 둘 다 9월 30일이다.
        assertTrue(attendance.record(account.userId(), Instant.parse("2026-09-30T14:59:00Z")));
        assertTrue(attendance.record(account.userId(), Instant.parse("2026-09-30T15:00:00Z")));
        JsonNode september = attendanceMonth(account, YearMonth.of(2026, 9));
        JsonNode october = attendanceMonth(account, YearMonth.of(2026, 10));
        assertEquals(List.of(LocalDate.of(2026, 9, 30)), dates(september.get("days")));
        assertEquals(List.of(LocalDate.of(2026, 10, 1)), dates(october.get("days")));
        assertEquals(1, october.get("lastMonthCount").asInt());
    }

    @Test
    void 월별_출석에_다른_사용자의_기록은_섞이지_않는다() throws Exception {
        Account account = signUp();
        Account other = signUp();
        YearMonth target = YearMonth.from(today()).minusMonths(1);
        seedAttendance(account.userId(), target.atDay(4));
        seedAttendance(other.userId(), target.atDay(4), target.atDay(9), target.atDay(10));
        assertEquals(List.of(target.atDay(4)), dates(attendanceMonth(account, target).get("days")));
        assertEquals(List.of(target.atDay(4), target.atDay(9), target.atDay(10)),
                dates(attendanceMonth(other, target).get("days")));
    }

    @Test
    void 기록_없는_계정과_미래_달은_빈_목록_200이고_범위_밖_달과_형식_오류는_400이다() throws Exception {
        Account account = signUp();
        LocalDate today = today();
        JsonNode empty = attendanceMonth(account, YearMonth.from(today));
        assertEquals(List.of(), dates(empty.get("days")));
        assertEquals(0, empty.get("monthCount").asInt());
        assertEquals(0, empty.get("lastMonthCount").asInt());
        assertEquals(0, empty.get("streak").get("currentDays").asInt());

        seedAttendance(account.userId(), today);
        YearMonth future = YearMonth.from(today).plusMonths(1);
        JsonNode next = attendanceMonth(account, future);
        assertEquals(List.of(), dates(next.get("days")));
        assertEquals(future.getYear(), next.get("year").asInt());
        assertEquals(future.getMonthValue(), next.get("month").asInt());
        assumeSameDay(today);
        assertEquals(1, next.get("lastMonthCount").asInt(), "다음 달에서 보면 이번 달이 지난달이다");

        for (String query : List.of("year=2026&month=0", "year=2026&month=13", "year=0&month=1",
                "year=2026&month=abc", "year=2026", "month=11")) {
            authed(get("/v0/learning/attendance?" + query), account.access())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        // 누락과 형식 오류는 어느 파라미터인지 메시지에 담는다 (PR #61 리뷰).
        authed(get("/v0/learning/attendance?year=2026"), account.access())
                .andExpect(jsonPath("$.message").value("month: 필수 값이 없습니다."));
        authed(get("/v0/learning/attendance?year=2026&month=abc"), account.access())
                .andExpect(jsonPath("$.message").value("month: 값의 형식이 올바르지 않습니다."));
        mockMvc.perform(get("/v0/learning/attendance?year=2026&month=11")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    // === 코스 목록의 완료 표시와 추천 (AC 2, AC 3) ===

    @Test
    void 코스는_채점을_마친_카드로_진도를_세고_완료되면_추천이_다음_코스로_넘어간다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        List<IntonationLearningDefinition.Course> lowest = coursesAtLowestLevel(current);
        IntonationLearningDefinition.Course first = lowest.get(0);

        JsonNode list = body(authed(get("/v0/learning/intonation-courses"), account.access())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        assertEquals(List.of(first.courseId()), recommendedIds(list.get("courses"), "courseId"));
        JsonNode summary = find(list.get("courses"), "courseId", first.courseId());
        assertEquals(0, summary.get("completedCardCount").asInt());
        assertFalse(summary.get("completed").asBoolean());

        // 실패한 시도와 다른 발행본의 시도는 세지 않는다. 같은 카드를 두 번 마쳐도 한 장이다.
        String version = current.definition().contentVersion();
        String card0 = first.cards().get(0).cardId();
        seedAttempt(account.userId(), version, card0, "FAILED");
        list = courses(account);
        assertEquals(0, find(list.get("courses"), "courseId", first.courseId()).get("completedCardCount").asInt());
        seedAttempt(account.userId(), version, card0, "COMPLETED");
        seedAttempt(account.userId(), version, card0, "COMPLETED");
        list = courses(account);
        assertEquals(1, find(list.get("courses"), "courseId", first.courseId()).get("completedCardCount").asInt());
        assertEquals(List.of(first.courseId()), recommendedIds(list.get("courses"), "courseId"));

        // 남은 카드를 다 마치면 코스 완료이고 추천은 같은 레벨의 다음 코스(없으면 다음 레벨)로 간다.
        for (IntonationLearningDefinition.Card card : first.cards().subList(1, first.cards().size())) {
            seedAttempt(account.userId(), version, card.cardId(), "COMPLETED");
        }
        list = courses(account);
        summary = find(list.get("courses"), "courseId", first.courseId());
        assertEquals(first.cards().size(), summary.get("completedCardCount").asInt());
        assertTrue(summary.get("completed").asBoolean());
        assertFalse(summary.get("recommended").asBoolean());
        String expectedNext = LearningProgress.recommend(current.definition().courses(),
                IntonationLearningDefinition.Course::level,
                course -> course.courseId().equals(first.courseId()), null).courseId();
        assertEquals(List.of(expectedNext), recommendedIds(list.get("courses"), "courseId"));
        assertEquals(expectedNext, home(account).get("recommendedCourseId").asString());

        // 다른 계정의 진도는 섞이지 않는다.
        Account other = signUp();
        assertEquals(0, find(courses(other).get("courses"), "courseId", first.courseId())
                .get("completedCardCount").asInt());
    }

    @Test
    void 추천은_최근_등급과_같은_레벨에서_시작한다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published intonation = intonationRegistry.current();
        int rank = intonation.definition().courses().stream().mapToInt(IntonationLearningDefinition.Course::level)
                .max().orElseThrow();
        levelTestRecords.record(account.userId(), entry(rank, Instant.now()));

        IntonationLearningDefinition.Course expectedCourse = intonation.definition().courses().stream()
                .filter(course -> course.level() >= rank).findFirst().orElseThrow();
        assertEquals(List.of(expectedCourse.courseId()), recommendedIds(courses(account).get("courses"), "courseId"));

        WordLearningDefinition.Set expectedSet = LearningProgress.recommend(wordRegistry.current().definition().sets(),
                WordLearningDefinition.Set::level, set -> false, rank);
        JsonNode sets = body(authed(get("/v0/learning/word-sets"), account.access()).andExpect(status().isOk()));
        List<String> recommendedSets = recommendedIds(sets.get("sets"), "setId");
        assertEquals(expectedSet == null ? List.of() : List.of(expectedSet.setId()), recommendedSets);

        JsonNode home = home(account);
        assertEquals(expectedCourse.courseId(), home.get("recommendedCourseId").asString());
        if (expectedSet == null) {
            assertTrue(home.get("recommendedSetId").isNull());
        } else {
            assertEquals(expectedSet.setId(), home.get("recommendedSetId").asString());
        }
    }

    // === 세트 목록의 완료 표시와 최근 정답률 (AC 2, AC 3) ===

    @Test
    void 세트를_완료하면_목록에_완료와_최근_정답률이_보이고_추천이_넘어간다() throws Exception {
        Account account = signUp();
        WordLearningRegistry.Published current = wordRegistry.current();
        WordLearningDefinition.Set first = LearningProgress.recommend(current.definition().sets(),
                WordLearningDefinition.Set::level, set -> false, null);

        JsonNode list = body(authed(get("/v0/learning/word-sets"), account.access())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        assertEquals(List.of(first.setId()), recommendedIds(list.get("sets"), "setId"));
        JsonNode summary = find(list.get("sets"), "setId", first.setId());
        assertFalse(summary.get("completed").asBoolean());
        assertTrue(summary.get("latestAccuracyPercent").isNull());

        // 첫 번째 시도는 전부 맞히고, 두 번째 시도는 첫 문항만 틀린다 - 최근 정답률은 두 번째 것이다.
        solve(account, first, 0);
        list = sets(account);
        summary = find(list.get("sets"), "setId", first.setId());
        assertTrue(summary.get("completed").asBoolean());
        assertEquals(100, summary.get("latestAccuracyPercent").asInt());
        assertFalse(summary.get("recommended").asBoolean());

        solve(account, first, 1);
        int itemCount = first.items().size();
        summary = find(sets(account).get("sets"), "setId", first.setId());
        assertEquals(WordAttemptResultResponse.accuracyPercent(itemCount - 1, itemCount),
                summary.get("latestAccuracyPercent").asInt());

        // 시작만 하고 끝내지 않은 시도는 완료가 아니다.
        WordLearningDefinition.Set next = LearningProgress.recommend(current.definition().sets(),
                WordLearningDefinition.Set::level, set -> set.setId().equals(first.setId()), null);
        authed(post("/v0/learning/word-sets/" + next.setId() + "/attempts"), account.access())
                .andExpect(status().isCreated());
        list = sets(account);
        assertFalse(find(list.get("sets"), "setId", next.setId()).get("completed").asBoolean());
        assertEquals(List.of(next.setId()), recommendedIds(list.get("sets"), "setId"));
        assertEquals(next.setId(), home(account).get("recommendedSetId").asString());
    }

    // === 세트 점수와 별, 구간 요약 (KAN-359) ===

    @Test
    void 토큰이_없으면_구간_요약은_401이다() throws Exception {
        mockMvc.perform(get("/v0/learning/levels")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void 억양_세트_점수는_카드별_최고_점수의_평균이고_실패와_처리_중은_세지_않는다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        String version = current.definition().contentVersion();
        IntonationLearningDefinition.Course course = coursesAtLowestLevel(current).get(0);
        List<IntonationLearningDefinition.Card> cards = course.cards();
        assertEquals(3, cards.size(), "이 테스트는 카드 3장짜리 코스를 전제로 점수를 고른다");
        String card0 = cards.get(0).cardId();
        String card1 = cards.get(1).cardId();
        String card2 = cards.get(2).cardId();

        // 완료하지 않은 코스는 점수가 null이고 별이 0이다.
        JsonNode summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertTrue(summary.get("score").isNull());
        assertEquals(0, summary.get("stars").asInt());

        // 카드 두 장만 마치면 아직 세트 점수가 없다. 실패와 처리 중 시도는 점수가 있어도 세지 않는다.
        seedAttempt(account.userId(), version, card0, "COMPLETED", 70, Instant.now());
        seedAttempt(account.userId(), version, card1, "COMPLETED", 70, Instant.now());
        seedAttempt(account.userId(), version, card2, "FAILED", 100, Instant.now());
        seedAttempt(account.userId(), version, card2, "RETRYABLE_FAILED", 100, Instant.now());
        seedAttempt(account.userId(), version, card2, "PROCESSING", 100, Instant.now());
        summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertEquals(2, summary.get("completedCardCount").asInt());
        assertFalse(summary.get("completed").asBoolean());
        assertTrue(summary.get("score").isNull());
        assertEquals(0, summary.get("stars").asInt());
        // 처리 중으로 심은 행이 다른 테스트의 혼잡 판정에 잡히지 않게 종결해 둔다.
        jdbc.update("update intonation_learning_attempt set status = 'FAILED' where user_id = ? and status = 'PROCESSING'",
                account.userId());

        // 세 장을 다 마치면 평균을 반올림한다 - (70 + 70 + 71) / 3 = 70.33 → 70.
        seedAttempt(account.userId(), version, card2, "COMPLETED", 71, Instant.now());
        summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertTrue(summary.get("completed").asBoolean());
        assertEquals(70, summary.get("score").asInt());
        assertEquals(3, summary.get("stars").asInt());

        // 점수가 내려간 재녹음은 세트 점수를 바꾸지 않는다. 실패한 재녹음도 마찬가지다.
        seedAttempt(account.userId(), version, card0, "COMPLETED", 10, Instant.now());
        seedAttempt(account.userId(), version, card1, "RETRYABLE_FAILED", null, Instant.now());
        summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertEquals(70, summary.get("score").asInt());
        assertEquals(3, summary.get("stars").asInt());

        // 카드 하나만 다시 녹음해 최고 점수가 오르면 세트 점수도 오른다. 다른 날의 시도도 합친다 -
        // (71 + 70 + 71) / 3 = 70.67 → 71.
        seedAttempt(account.userId(), version, card0, "COMPLETED", 71, Instant.now().minus(3, ChronoUnit.DAYS));
        summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertEquals(71, summary.get("score").asInt());

        // 다른 발행본과 다른 계정의 시도는 섞이지 않는다.
        Account other = signUp();
        JsonNode otherSummary = find(courses(other).get("courses"), "courseId", course.courseId());
        assertTrue(otherSummary.get("score").isNull());
        assertEquals(0, otherSummary.get("stars").asInt());
    }

    @Test
    void 억양_세트가_0점이어도_완료했으면_별_1개다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published current = intonationRegistry.current();
        IntonationLearningDefinition.Course course = coursesAtLowestLevel(current).get(0);
        seedCourse(account.userId(), current.definition().contentVersion(), course, 0);

        JsonNode summary = find(courses(account).get("courses"), "courseId", course.courseId());
        assertEquals(0, summary.get("score").asInt());
        assertEquals(1, summary.get("stars").asInt());
    }

    @Test
    void 단어_세트_점수는_완료한_시도의_최고_정답률이고_완료_응답의_별은_이번_시도_기준이다() throws Exception {
        Account account = signUp();
        WordLearningDefinition.Set set = LearningProgress.recommend(wordRegistry.current().definition().sets(),
                WordLearningDefinition.Set::level, candidate -> false, null);
        int itemCount = set.items().size();

        JsonNode summary = find(sets(account).get("sets"), "setId", set.setId());
        assertTrue(summary.get("score").isNull());
        assertEquals(0, summary.get("stars").asInt());

        // 시작만 한 시도는 세지 않는다.
        authed(post("/v0/learning/word-sets/" + set.setId() + "/attempts"), account.access())
                .andExpect(status().isCreated());
        summary = find(sets(account).get("sets"), "setId", set.setId());
        assertTrue(summary.get("score").isNull());
        assertEquals(0, summary.get("stars").asInt());

        // 전부 맞히면 100점에 별 3개다.
        JsonNode result = solve(account, set, 0);
        assertEquals(100, result.get("accuracyPercent").asInt());
        assertEquals(3, result.get("stars").asInt());
        summary = find(sets(account).get("sets"), "setId", set.setId());
        assertEquals(100, summary.get("score").asInt());
        assertEquals(3, summary.get("stars").asInt());

        // 다시 풀어 전부 틀리면 이번 시도는 0점에 별 1개이고, 목록의 세트 점수와 별은 최고 기록 그대로다.
        result = solve(account, set, itemCount);
        assertEquals(0, result.get("accuracyPercent").asInt());
        assertEquals(1, result.get("stars").asInt());
        summary = find(sets(account).get("sets"), "setId", set.setId());
        assertEquals(0, summary.get("latestAccuracyPercent").asInt());
        assertEquals(100, summary.get("score").asInt());
        assertEquals(3, summary.get("stars").asInt());
        assertTrue(summary.get("completed").asBoolean());
    }

    @Test
    void 구간_요약은_구간_1부터_5를_주고_새_계정은_등급이_있어도_구간_1만_열려_있다() throws Exception {
        Account account = signUp();
        // 등급은 열림에 쓰지 않는다 - 가장 높은 등급이어도 구간 1부터다.
        levelTestRecords.record(account.userId(), entry(5, Instant.now()));

        JsonNode summary = levels(account);
        assertEquals(0, summary.get("totalStars").asInt());
        assertEquals(2, summary.get("starThresholds").size());
        assertEquals(30, summary.get("starThresholds").get(0).asInt());
        assertEquals(70, summary.get("starThresholds").get(1).asInt());
        assertEquals(5, summary.get("levels").size());
        for (int i = 0; i < 5; i++) {
            JsonNode level = summary.get("levels").get(i);
            assertEquals(i + 1, level.get("level").asInt());
            assertEquals(9, level.get("setCount").asInt());
            assertEquals(0, level.get("completedSetCount").asInt());
            assertEquals(0, level.get("collectedStars").asInt());
            assertEquals(21, level.get("requiredStars").asInt());
            assertEquals(i == 0, level.get("unlocked").asBoolean(), "구간 " + (i + 1));
        }
        // 등급은 추천에만 영향을 준다.
        int recommendedLevel = find(courses(account).get("courses"), "courseId",
                home(account).get("recommendedCourseId").asString()).get("level").asInt();
        assertEquals(5, recommendedLevel);
    }

    @Test
    void 다음_구간은_별이_21개가_되는_순간_열리고_완주만으로는_열리지_않으며_다시_잠기지_않는다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published intonation = intonationRegistry.current();
        WordLearningRegistry.Published word = wordRegistry.current();
        String intonationVersion = intonation.definition().contentVersion();
        String wordVersion = word.definition().contentVersion();
        List<IntonationLearningDefinition.Course> courses = coursesAtLowestLevel(intonation);
        int lowest = courses.get(0).level();
        List<WordLearningDefinition.Set> wordSets = word.definition().sets().stream()
                .filter(set -> set.level() == lowest).toList();
        assertEquals(1, lowest);
        assertEquals(5, courses.size());
        assertEquals(4, wordSets.size());

        // 구간 1의 9세트를 모두 완료했지만 전부 0점이면 별은 9개다 - 완주만으로는 구간 2가 열리지 않는다.
        for (IntonationLearningDefinition.Course course : courses) {
            seedCourse(account.userId(), intonationVersion, course, 0);
        }
        for (WordLearningDefinition.Set set : wordSets) {
            seedWordAttempt(account.userId(), wordVersion, set, 0);
        }
        JsonNode summary = levels(account);
        assertEquals(9, level(summary, 1).get("completedSetCount").asInt());
        assertEquals(9, level(summary, 1).get("collectedStars").asInt());
        assertTrue(level(summary, 1).get("unlocked").asBoolean());
        assertFalse(level(summary, 2).get("unlocked").asBoolean());

        // 억양 5코스를 100점으로 올리면 15 + 4 = 19개, 단어 한 세트를 50점대로 올리면 20개 - 아직 잠겨 있다.
        for (IntonationLearningDefinition.Course course : courses) {
            seedCourse(account.userId(), intonationVersion, course, 100);
        }
        assertEquals(19, level(levels(account), 1).get("collectedStars").asInt());
        WordLearningDefinition.Set raised = wordSets.get(0);
        int itemCount = raised.items().size();
        assertEquals(3, itemCount, "이 테스트는 문항 3개짜리 세트를 전제로 정답 수를 고른다");
        seedWordAttempt(account.userId(), wordVersion, raised, 2);
        summary = levels(account);
        assertEquals(20, level(summary, 1).get("collectedStars").asInt());
        assertFalse(level(summary, 2).get("unlocked").asBoolean());

        // 그 세트를 전부 맞히면 21개 - 닿는 순간 구간 2가 열린다. 구간 3은 구간 2의 별로 정하므로 그대로 잠겨 있다.
        seedWordAttempt(account.userId(), wordVersion, raised, itemCount);
        summary = levels(account);
        assertEquals(21, level(summary, 1).get("collectedStars").asInt());
        assertEquals(21, level(summary, 1).get("requiredStars").asInt());
        assertTrue(level(summary, 2).get("unlocked").asBoolean());
        assertFalse(level(summary, 3).get("unlocked").asBoolean());

        // 다시 풀어 점수가 내려가도 별이 줄지 않고 열린 구간이 다시 잠기지 않는다.
        seedWordAttempt(account.userId(), wordVersion, raised, 0);
        seedCourse(account.userId(), intonationVersion, courses.get(0), 5);
        summary = levels(account);
        assertEquals(21, level(summary, 1).get("collectedStars").asInt());
        assertTrue(level(summary, 2).get("unlocked").asBoolean());

        // 모은 별은 필요 별을 넘을 수 있다 - 9세트가 모두 별 3개면 27개다.
        for (WordLearningDefinition.Set set : wordSets) {
            seedWordAttempt(account.userId(), wordVersion, set, set.items().size());
        }
        summary = levels(account);
        assertEquals(27, level(summary, 1).get("collectedStars").asInt());
        assertEquals(21, level(summary, 1).get("requiredStars").asInt());

        // 전체 별은 두 목록의 별 합과 같다.
        assertEquals(starSum(courses(account).get("courses")) + starSum(sets(account).get("sets")),
                summary.get("totalStars").asInt());
        assertEquals(27, summary.get("totalStars").asInt());

        // 다른 계정의 별은 섞이지 않는다.
        assertEquals(0, levels(signUp()).get("totalStars").asInt());
    }

    @Test
    void 잠긴_구간의_단어_세트도_시도_시작은_지금처럼_성공한다() throws Exception {
        Account account = signUp();
        JsonNode summary = levels(account);
        JsonNode last = summary.get("levels").get(summary.get("levels").size() - 1);
        assertFalse(last.get("unlocked").asBoolean());
        WordLearningDefinition.Set locked = wordRegistry.current().definition().sets().stream()
                .filter(set -> set.level() == last.get("level").asInt()).findFirst().orElseThrow();

        authed(post("/v0/learning/word-sets/" + locked.setId() + "/attempts"), account.access())
                .andExpect(status().isCreated());
        // 잠긴 구간의 세트를 완료해 얻은 별도 그대로 센다 - 열림은 표시일 뿐이다.
        JsonNode result = solve(account, locked, 0);
        assertEquals(3, result.get("stars").asInt());
        assertEquals(3, level(levels(account), last.get("level").asInt()).get("collectedStars").asInt());
    }

    @Test
    void 카드마다_시도가_10건인_계정도_구간_요약을_조회마다_계산한다() throws Exception {
        Account account = signUp();
        IntonationLearningRegistry.Published intonation = intonationRegistry.current();
        WordLearningRegistry.Published word = wordRegistry.current();
        int courseCount = intonation.definition().courses().size();
        int setCount = word.definition().sets().size();
        for (int round = 0; round < 10; round++) {
            for (IntonationLearningDefinition.Course course : intonation.definition().courses()) {
                seedCourse(account.userId(), intonation.definition().contentVersion(), course, 40 + round * 5);
            }
            for (WordLearningDefinition.Set set : word.definition().sets()) {
                seedWordAttempt(account.userId(), word.definition().contentVersion(), set,
                        round == 9 ? set.items().size() : 0);
            }
        }
        levels(account);
        long started = System.nanoTime();
        int rounds = 20;
        JsonNode summary = null;
        for (int i = 0; i < rounds; i++) {
            summary = levels(account);
        }
        long averageMs = (System.nanoTime() - started) / rounds / 1_000_000;
        System.out.println("KAN-359 구간 요약 평균 응답 시간(ms) = " + averageMs + " (억양 시도 "
                + count("select count(*) from intonation_learning_attempt where user_id = ?", account.userId())
                + "건, 단어 시도 "
                + count("select count(*) from word_set_attempt where user_id = ?", account.userId()) + "건)");
        // 최고 점수 85점과 정답률 100이라 모든 세트가 별 3개다.
        assertEquals((courseCount + setCount) * 3, summary.get("totalStars").asInt());
    }

    // === 탈퇴 (AC 5) ===

    @Test
    void 탈퇴하면_최근_등급과_학습_기록과_출석이_전부_파기된다() throws Exception {
        Account account = signUp();
        completeLevelTest(account);
        IntonationLearningDefinition.Course course = intonationRegistry.current().definition().courses().get(0);
        seedAttempt(account.userId(), intonationRegistry.current().definition().contentVersion(),
                course.cards().get(0).cardId(), "COMPLETED");
        solve(account, wordRegistry.current().definition().sets().get(0), 1);

        Account other = signUp();
        completeLevelTest(other);

        assertEquals(1, count("select count(*) from level_test_record where user_id = ?", account.userId()));
        assertEquals(1, count("select count(*) from learning_attendance where user_id = ?", account.userId()));
        authed(post("/v0/users/me/withdrawal"), account.access()).andExpect(status().isNoContent());

        assertEquals(0, count("select count(*) from level_test_record where user_id = ?", account.userId()));
        assertEquals(0, count("select count(*) from intonation_learning_attempt where user_id = ?", account.userId()));
        assertEquals(0, count("select count(*) from word_set_attempt where user_id = ?", account.userId()));
        assertEquals(0, count("select count(*) from word_wrong_answer where user_id = ?", account.userId()));
        assertEquals(0, count("select count(*) from learning_attendance where user_id = ?", account.userId()));
        assertEquals(1, count("select count(*) from level_test_record where user_id = ?", other.userId()));
        assertEquals(1, count("select count(*) from learning_attendance where user_id = ?", other.userId()));
        authed(get("/v0/learning/home"), account.access()).andExpect(status().isUnauthorized());
    }

    // === 도우미 ===

    private record Account(String access, UUID userId) {
    }

    private static LevelTestRecords.Entry entry(int rank, Instant completedAt) {
        return new LevelTestRecords.Entry("gn-2026.08.1", "sv-0.3", "T" + rank, "등급 " + rank, rank, 5, completedAt);
    }

    private static List<IntonationLearningDefinition.Course> coursesAtLowestLevel(
            IntonationLearningRegistry.Published current) {
        int lowest = current.definition().courses().stream().mapToInt(IntonationLearningDefinition.Course::level)
                .min().orElseThrow();
        return current.definition().courses().stream().filter(course -> course.level() == lowest).toList();
    }

    /** 계정 귀속 세션(§3.1)을 만들어 완주한다 - 문항 구성은 테스트 픽스처 정의(seed)다. */
    private SessionHandle completeLevelTest(Account account) throws Exception {
        JsonNode created = body(authed(post("/v0/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"),
                account.access()).andExpect(status().isCreated()));
        SessionHandle session = new SessionHandle(created.get("sessionId").asString(),
                created.get("sessionToken").asString());
        flow.answerVocab(session);
        flow.completeVoice(session);
        flow.complete(session, "complete-" + session.id());
        return session;
    }

    /** 세트 하나를 시작부터 완료까지 푼다 - 앞의 {@code wrongCount}개 문항은 틀린다. */
    private JsonNode solve(Account account, WordLearningDefinition.Set set, int wrongCount) throws Exception {
        String attemptId = body(authed(post("/v0/learning/word-sets/" + set.setId() + "/attempts"), account.access())
                .andExpect(status().isCreated())).get("attemptId").asString();
        for (int i = 0; i < set.items().size(); i++) {
            WordLearningDefinition.Item item = set.items().get(i);
            String choice = i < wrongCount
                    ? item.choices().stream().map(WordLearningDefinition.Choice::choiceId)
                    .filter(id -> !id.equals(item.correctChoiceId())).findFirst().orElseThrow()
                    : item.correctChoiceId();
            authed(post("/v0/learning/word-attempts/" + attemptId + "/items/" + item.itemId() + "/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "k-" + item.itemId())
                    .content("{\"choiceId\":\"" + choice + "\"}"), account.access())
                    .andExpect(status().isOk());
        }
        return body(authed(post("/v0/learning/word-attempts/" + attemptId + "/complete"), account.access())
                .andExpect(status().isOk()));
    }

    private void seedAttempt(UUID userId, String contentVersion, String cardId, String status) {
        seedAttempt(userId, contentVersion, cardId, status, null, Instant.now());
    }

    /** 변환 점수와 접수 시각을 정해 심는다 (KAN-359) - 세트 점수는 카드별 최고 점수로 계산한다. */
    private void seedAttempt(UUID userId, String contentVersion, String cardId, String status, Integer score,
                             Instant at) {
        Timestamp time = Timestamp.from(at);
        jdbc.update("""
                        insert into intonation_learning_attempt
                            (id, user_id, content_version, card_id, idempotency_key, status, created_at, finished_at,
                             score)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, "la_" + UUID.randomUUID(), userId, contentVersion, cardId, "k-" + UUID.randomUUID(),
                status, time, time, score);
    }

    /** 코스의 모든 카드에 같은 점수의 완료 시도를 하나씩 심는다. */
    private void seedCourse(UUID userId, String contentVersion, IntonationLearningDefinition.Course course,
                            int score) {
        for (IntonationLearningDefinition.Card card : course.cards()) {
            seedAttempt(userId, contentVersion, card.cardId(), "COMPLETED", score, Instant.now());
        }
    }

    /** 완료한 단어 시도를 표에 직접 심는다 - 답안 API를 거치지 않아 요청 제한과 무관하게 여러 건을 만든다. */
    private void seedWordAttempt(UUID userId, String contentVersion, WordLearningDefinition.Set set,
                                 int correctCount) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                        insert into word_set_attempt
                            (id, user_id, content_version, set_id, item_count, correct_count, started_at, completed_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """, "wa_" + UUID.randomUUID(), userId, contentVersion, set.setId(), set.items().size(),
                correctCount, now, now);
    }

    private JsonNode levels(Account account) throws Exception {
        return body(authed(get("/v0/learning/levels"), account.access())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
    }

    private static JsonNode level(JsonNode levels, int level) {
        for (JsonNode item : levels.get("levels")) {
            if (item.get("level").asInt() == level) {
                return item;
            }
        }
        throw new AssertionError("구간 " + level + "이 요약에 없다: " + levels);
    }

    private static int starSum(JsonNode items) {
        int sum = 0;
        for (JsonNode item : items) {
            sum += item.get("stars").asInt();
        }
        return sum;
    }

    private void seedAttendance(UUID userId, LocalDate... dates) {
        for (LocalDate date : dates) {
            jdbc.update("insert into learning_attendance (user_id, attended_on) values (?, ?)", userId,
                    Date.valueOf(date));
        }
    }

    private static LocalDate today() {
        return LearningAttendance.attendanceDate(Instant.now());
    }

    /**
     * 날짜에 걸린 단언 바로 앞에서 부른다 (PR #43 리뷰). 서비스가 실제 시각으로 출석을 남기므로 실행 도중 KST 자정을 지나면
     * 정상 구현도 날짜가 갈린다 - 그때는 판정할 수 없어 실패 대신 건너뛴다. 경계 규칙 자체는 LearningAttendanceTest가
     * 고정 시각으로 본다.
     */
    private static void assumeSameDay(LocalDate start) {
        assumeTrue(start.equals(today()), "실행 도중 KST 자정을 지나 날짜 판정을 건너뛴다");
    }

    private List<LocalDate> attendedDates(UUID userId) {
        return jdbc.query("select attended_on from learning_attendance where user_id = ? order by attended_on",
                (rs, rowNum) -> rs.getDate(1).toLocalDate(), userId);
    }

    private JsonNode home(Account account) throws Exception {
        return body(authed(get("/v0/learning/home"), account.access())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
    }

    private JsonNode attendanceMonth(Account account, YearMonth month) throws Exception {
        return body(authed(get("/v0/learning/attendance?year=" + month.getYear() + "&month=" + month.getMonthValue()),
                account.access())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
    }

    private static List<LocalDate> dates(JsonNode days) {
        List<LocalDate> values = new ArrayList<>();
        for (JsonNode day : days) {
            values.add(LocalDate.parse(day.get("date").asString()));
        }
        return values;
    }

    private JsonNode courses(Account account) throws Exception {
        return body(authed(get("/v0/learning/intonation-courses"), account.access()).andExpect(status().isOk()));
    }

    private JsonNode sets(Account account) throws Exception {
        return body(authed(get("/v0/learning/word-sets"), account.access()).andExpect(status().isOk()));
    }

    private static List<String> recommendedIds(JsonNode items, String idField) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.get("recommended").asBoolean()) {
                ids.add(item.get(idField).asString());
            }
        }
        return ids;
    }

    private static JsonNode find(JsonNode items, String idField, String id) {
        for (JsonNode item : items) {
            if (item.get(idField).asString().equals(id)) {
                return item;
            }
        }
        throw new AssertionError(id + "가 목록에 없다: " + items);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String access) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access));
    }

    private Account signUp() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", "KAKAO");
        request.put("accessToken", "fake:sub-" + UUID.randomUUID());
        request.put("privacyConsent", true);
        request.put("privacyPolicyVersion", POLICY_VERSION);
        String access = body(mockMvc.perform(post("/v0/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())).get("accessToken").asString();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", "user@example.com");
        profile.put("name", "박유현");
        profile.put("birthDate", "1999-03-02");
        profile.put("gender", "MALE");
        profile.put("region", "GYEONGNAM");
        mockMvc.perform(put("/v0/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());
        UUID userId = UUID.fromString(body(authed(get("/v0/users/me"), access).andExpect(status().isOk()))
                .get("user").get("id").asString());
        return new Account(access, userId);
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
