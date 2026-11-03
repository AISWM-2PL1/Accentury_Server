package app.accentury.backend.learning;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /v0/learning/attendance?year=&month=}의 200 응답 (KAN-314, 명세서 §3.20) - 홈 앱바의 월 달력 시트가 쓴다.
 *
 * @param year         요청한 연도
 * @param month        요청한 달 (1부터 12)
 * @param attendedDays 그 달(KST)에 출석한 일(day), 오름차순 - 없으면 빈 목록
 * @param monthCount   {@code attendedDays}의 개수 - 시트의 "이번 달 출석 수"가 같은 값을 보이도록 서버가 센다
 * @param streak       홈(§3.20)과 같은 연속 출석 - 시트 헤더의 "연속 N일"
 * @param today        서버 기준 KST 오늘 날짜 - 기기 시계와 어긋나지 않게 달력의 "오늘" 표시는 이 값을 쓴다
 */
public record MonthlyAttendanceResponse(int year, int month, List<Integer> attendedDays, int monthCount,
                                        LearningAttendance.Streak streak, LocalDate today) {
}
