package app.accentury.backend.learning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 출석 날짜 규칙 (KAN-286, 명세서 §3.20) - DB 없이 본다. 연속 출석은 SQL 한 문장이라(KAN-314) 기록, 홈 응답, 달력과
 * 함께 {@code LearningProgressApiTest}가 본다.
 */
class LearningAttendanceTest {

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
}
