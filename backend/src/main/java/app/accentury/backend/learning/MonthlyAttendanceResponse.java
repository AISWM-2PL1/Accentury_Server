package app.accentury.backend.learning;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /v0/learning/attendance?year=&month=}의 200 응답 (KAN-314, 명세서 §3.20) - 홈 앱바의 월 달력 시트가 쓴다.
 *
 * @param year           요청한 연도
 * @param month          요청한 달 (1부터 12)
 * @param days           그 달(KST)에 출석한 날, 오름차순 - 없으면 빈 목록. 날마다 그날 마친 학습 항목이 붙는다
 * @param monthCount     {@code days}의 개수 - 시트의 "이번 달 출석 수"가 같은 값을 보이도록 서버가 센다
 * @param lastMonthCount 지난달 출석 수 - 시트의 지난달 대비
 * @param streak         홈(§3.20)과 같은 연속 출석 - 시트 헤더의 "연속 N일"
 * @param today          서버 기준 KST 오늘 날짜 - 기기 시계와 어긋나지 않게 달력의 "오늘" 표시는 이 값을 쓴다
 */
public record MonthlyAttendanceResponse(int year, int month, List<Day> days, int monthCount, int lastMonthCount,
                                        LearningAttendance.Streak streak, LocalDate today) {

    /**
     * 출석한 날 하나 - {@code items}는 그날 마친 학습을 마친 순서로. 출석은 남았는데 시도 기록이 없는 날(옛 레벨테스트
     * 완주 등)은 빈 목록이다.
     */
    public record Day(LocalDate date, List<Item> items) {
    }

    /**
     * 그날 마친 학습 하나 (KAN-314, 2026-11-03 결정).
     *
     * @param kind  {@code SET} 단어 세트 완료(시도마다 1건), {@code COURSE} 억양 코스(그날 채점을 마친 카드를 코스로 묶어
     *              1건), {@code LEVEL_TEST} 레벨테스트 완주(최근 완주 1건만 남아 그날에만 붙는다)
     * @param id    세트 id, 코스 id, 레벨테스트는 테스트 버전
     * @param title 세트와 코스의 제목, 레벨테스트는 등급 이름
     * @param done  세트는 정답 수, 코스는 그날 채점을 마친 카드 수, 레벨테스트는 1
     * @param total 세트는 문항 수, 코스는 코스의 카드 수, 레벨테스트는 1
     */
    public record Item(Kind kind, String id, String title, int done, int total) {
    }

    public enum Kind { SET, COURSE, LEVEL_TEST }
}
