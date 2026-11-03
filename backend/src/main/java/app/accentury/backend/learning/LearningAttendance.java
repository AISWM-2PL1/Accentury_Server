package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 계정의 출석일 기록과 연속 출석 (KAN-286, V16 {@code learning_attendance}, 명세서 §3.20).
 * <p>
 * 출석 1일은 계정이 그날 레벨테스트 완주, 단어 세트 완료, 억양 카드 채점 완료 가운데 하나를 한 날이다 (2026-10-14 결정).
 * 날짜 경계는 Asia/Seoul 자정으로 고정한다. 홈 조회는 읽기만 한다 - 앱을 여는 것은 출석이 아니다.
 * <p>
 * <b>쓰기는 각 완료 트랜잭션이 커밋된 뒤에 한다</b> - {@link LevelTestRecords}와 같은 이유다. 완료는 세션이나 시도 행을
 * 잠근 채 쓰고 탈퇴는 계정 행을 잠근 채 그 행들을 고치거나 지우므로, 같은 트랜잭션에서 FK가 걸린 이 행을 넣으면 교착이
 * 된다. 그래서 계정 행의 공유 잠금 아래 한 문장으로 넣는다 - 탈퇴가 먼저면 넣지 않고, 넣기가 먼저면 탈퇴가
 * {@link LearningRecords#purge}로 지운다. 기록이 실패해도 호출부의 완료 응답은 바뀌지 않는다 (호출부가 경고 로그만 남긴다).
 */
@Component
public class LearningAttendance {

    /** 출석 날짜의 경계 - 해외 사용자 대응은 범위 밖이다 (2026-10-14 결정). */
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 홈의 연속 출석 - {@code lastAttendedDate}는 출석이 한 번도 없으면 null이다. */
    public record Streak(int currentDays, boolean attendedToday, @Nullable LocalDate lastAttendedDate) {

        static final Streak NONE = new Streak(0, false, null);
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    LearningAttendance(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    LearningAttendance(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 완료 시각이 속한 날의 출석을 남긴다. 자기 트랜잭션(자동 커밋 한 문장)이라 완료 트랜잭션 밖에서 부른다.
     *
     * @return 그날 첫 출석이라 행을 넣었으면 true. 이미 출석한 날이거나 탈퇴한 계정이면 false
     */
    public boolean record(UUID userId, Instant at) {
        return jdbc.sql("""
                        insert into learning_attendance (user_id, attended_on)
                        select :userId, :attendedOn
                        where exists (select 1 from app_user where id = :userId and deleted_at is null for share)
                        on conflict (user_id, attended_on) do nothing
                        """)
                .param("userId", userId)
                .param("attendedOn", Date.valueOf(attendanceDate(at)))
                .update() > 0;
    }

    /** 홈 (H-1) - 오늘(KST) 기준 연속 출석. */
    Streak streak(UUID userId) {
        return streak(userId, attendanceDate(Instant.now(clock)));
    }

    /**
     * 연속 출석 (§3.20) - 홈과 달력이 같은 문장을 쓴다 (PR #61 리뷰). 오늘 출석했으면 오늘부터, 아직이면 어제부터 거슬러
     * 끊김 없이 이어진 날을 센다 - 오늘이 끝나기 전에는 끊긴 것으로 보지 않는다. 어제와 오늘 모두 없으면 0이다.
     * <p>
     * 날짜에서 내림차순 행 번호를 더하면 이어진 날들은 같은 값(섬)이 되므로, 가장 최근 날짜가 속한 섬의 크기가 이어진
     * 일수다. SQL이 한 행만 돌려주어 달을 넘길 때마다 이력을 다시 받아오지 않는다.
     */
    Streak streak(UUID userId, LocalDate today) {
        return jdbc.sql("""
                        with ranked as (
                            select attended_on,
                                   attended_on + (row_number() over (order by attended_on desc))::int as island
                              from learning_attendance
                             where user_id = :userId and attended_on <= :today
                        ),
                        latest as (select attended_on, island from ranked order by attended_on desc limit 1)
                        select latest.attended_on as last_date,
                               (select count(*) from ranked where ranked.island = latest.island) as island_days
                          from latest
                        """)
                .param("userId", userId)
                .param("today", Date.valueOf(today))
                .query((rs, rowNum) -> {
                    LocalDate last = rs.getDate("last_date").toLocalDate();
                    boolean attendedToday = last.equals(today);
                    boolean unbroken = attendedToday || last.equals(today.minusDays(1));
                    return new Streak(unbroken ? rs.getInt("island_days") : 0, attendedToday, last);
                })
                .optional()
                .orElse(Streak.NONE);
    }

    /** 달력 시트의 출석 부분 (KAN-314) - 그 달의 출석 날짜(오름차순), 지난달 출석 수, 홈과 같은 연속 출석, 오늘. */
    record Calendar(List<LocalDate> days, int lastMonthCount, Streak streak, LocalDate today) {
    }

    /**
     * 홈 앱바 달력 시트 (KAN-314, §3.20) - 한 달(KST)의 출석 날짜와 지난달 출석 수, 홈과 같은 연속 출석, 서버의 오늘.
     * <p>
     * 달은 KST 날짜의 달이다 - 출석일 자체가 KST 날짜로 저장돼 있어 경계 변환이 없다. 미래 달이나 기록이 없는 계정은
     * 빈 목록이다. 오늘은 한 번만 정해 {@code today}와 {@code streak}가 같은 기준일을 본다 (PR #61 리뷰). 날짜 목록은
     * 지난달부터 그 달까지만 읽고(기본 키 범위), 연속 출석은 홈과 같은 {@link #streak(UUID, LocalDate)}로 한 행만
     * 받는다. 두 문장이 같은 스냅샷을 보는 것은 호출부({@link LearningProgress})의 REPEATABLE READ 트랜잭션이 맡는다.
     * 그날 무엇을 했는지는 이 표에 없다 - {@link LearningProgress}가 시도 기록에서 붙인다.
     */
    Calendar calendar(UUID userId, YearMonth month) {
        LocalDate today = attendanceDate(Instant.now(clock));
        LocalDate first = month.atDay(1);
        List<LocalDate> dates = jdbc.sql("""
                        select attended_on from learning_attendance
                         where user_id = :userId and attended_on between :previousFirst and :last
                         order by attended_on
                        """)
                .param("userId", userId)
                .param("previousFirst", Date.valueOf(month.minusMonths(1).atDay(1)))
                .param("last", Date.valueOf(month.atEndOfMonth()))
                .query((rs, rowNum) -> rs.getDate("attended_on").toLocalDate())
                .list();
        List<LocalDate> days = new ArrayList<>();
        int lastMonthCount = 0;
        for (LocalDate date : dates) {
            if (date.isBefore(first)) {
                lastMonthCount++;
            } else {
                days.add(date);
            }
        }
        return new Calendar(days, lastMonthCount, streak(userId, today), today);
    }

    /** 한 시각이 속한 출석 날짜 - Asia/Seoul 자정이 경계다. */
    static LocalDate attendanceDate(Instant at) {
        return LocalDate.ofInstant(at, ZONE);
    }

    /** 탈퇴 - 이 계정의 출석일을 전부 지운다. 탈퇴 트랜잭션 안에서 부른다. 지운 행 수. */
    int purge(UUID userId) {
        return jdbc.sql("delete from learning_attendance where user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
