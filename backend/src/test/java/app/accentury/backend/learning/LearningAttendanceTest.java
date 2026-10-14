package app.accentury.backend.learning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 출석 날짜와 연속 출석 계산 (KAN-286, 명세서 §3.20) - DB 없이 규칙만 본다. 기록과 홈 응답은
 * {@code LearningProgressApiTest}가 본다.
 */
class LearningAttendanceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    // === 날짜 경계 (AC 4) ===

    @Test
    void 날짜는_KST_자정에서_갈린다() {
        // KST 23:59는 UTC 14:59, KST 00:01은 UTC 15:01이다 - UTC 날짜는 같아도 출석 날짜는 다르다.
        assertEquals(LocalDate.of(2026, 10, 14), LearningAttendance.attendanceDate(Instant.parse("2026-10-14T14:59:00Z")));
        assertEquals(LocalDate.of(2026, 10, 15), LearningAttendance.attendanceDate(Instant.parse("2026-10-14T15:01:00Z")));
        assertEquals(LocalDate.of(2026, 10, 15), LearningAttendance.attendanceDate(Instant.parse("2026-10-14T15:00:00Z")));
        // UTC로는 전날 밤인 KST 오전도 KST 날짜를 따른다.
        assertEquals(LocalDate.of(2026, 10, 14), LearningAttendance.attendanceDate(Instant.parse("2026-10-13T23:30:00Z")));
    }

    // === 연속 일수 (AC 2, AC 3) ===

    @Test
    void 출석이_없으면_0이고_마지막_날짜도_없다() {
        assertEquals(new LearningAttendance.Streak(0, false, null), LearningAttendance.streak(List.of(), TODAY));
    }

    @Test
    void 오늘_출석했으면_오늘부터_센다() {
        assertEquals(new LearningAttendance.Streak(2, true, TODAY),
                LearningAttendance.streak(List.of(TODAY, TODAY.minusDays(1)), TODAY));
        assertEquals(new LearningAttendance.Streak(1, true, TODAY),
                LearningAttendance.streak(List.of(TODAY, TODAY.minusDays(2)), TODAY));
    }

    @Test
    void 오늘_아직이면_어제까지의_값이다() {
        assertEquals(new LearningAttendance.Streak(2, false, TODAY.minusDays(1)),
                LearningAttendance.streak(List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4)), TODAY));
    }

    @Test
    void 어제를_건너뛰면_0이다() {
        assertEquals(new LearningAttendance.Streak(0, false, TODAY.minusDays(2)),
                LearningAttendance.streak(List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY));
    }
}
