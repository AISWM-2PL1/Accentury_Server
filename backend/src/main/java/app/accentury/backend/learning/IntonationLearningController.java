package app.accentury.backend.learning;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * 억양 학습 콘텐츠 API (KAN-264, 명세서 §3.17) - 전부 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * <p>
 * 조회뿐이라 요청 제한(§2.5)을 걸지 않는다. 응답은 계정 상태에 따라 달라지므로(KAN-268의 완료 표시와 추천) 캐시하지
 * 않는다. 학습 홈 상태(§3.20)와 홈 달력 시트의 월별 출석(KAN-314)도 조회뿐이라 여기 둔다.
 */
@RestController
@RequestMapping("/v0/learning")
class IntonationLearningController {

    private final IntonationLearningRegistry registry;
    private final LearningProgress progress;

    IntonationLearningController(IntonationLearningRegistry registry, LearningProgress progress) {
        this.registry = registry;
        this.progress = progress;
    }

    /** 코스 목록 (A-1) - 계정의 완료 표시와 추천 1개(KAN-268)를 채운다. 200 / 401. */
    @GetMapping("/intonation-courses")
    ResponseEntity<IntonationCourseListResponse> listCourses(@AuthenticatedUser AppUser user) {
        return noStore(progress.courses(user.id()));
    }

    /** 학습 홈 상태 (H-1, KAN-268, §3.20) - 레벨테스트 완주 여부와 등급, 두 목록의 추천. 200 / 401. */
    @GetMapping("/home")
    ResponseEntity<LearningHomeResponse> home(@AuthenticatedUser AppUser user) {
        return noStore(progress.home(user.id()));
    }

    /**
     * 월별 출석 (KAN-314, §3.20) - 홈 앱바 달력 시트. 그 달(KST)에 출석한 날과 그날 마친 학습 항목, 이번 달과 지난달
     * 출석 수, 홈과 같은 연속 출석, 서버의 오늘 날짜. 기록이 없는 계정과 미래 달은 빈 목록 200이다. 200 / 400 {@code VALIDATION_FAILED}({@code year}나
     * {@code month}가 없거나 정수가 아니거나 범위 밖) / 401.
     *
     * @param year  연도 (1부터 9999)
     * @param month 달 (1부터 12)
     */
    @GetMapping("/attendance")
    ResponseEntity<MonthlyAttendanceResponse> attendance(@AuthenticatedUser AppUser user,
                                                         @RequestParam int year, @RequestParam int month) {
        // 없거나 정수가 아닌 값은 프레임워크가 컨트롤러 진입 전에 400 VALIDATION_FAILED로 끊는다. 여기는 범위만 본다.
        if (month < 1 || month > 12) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "month는 1 이상 12 이하의 정수여야 합니다.");
        }
        if (year < 1 || year > 9999) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "year는 1 이상 9999 이하의 정수여야 합니다.");
        }
        return noStore(progress.attendance(user.id(), YearMonth.of(year, month)));
    }

    /** 코스 상세 - 대사 카드 (A-2). 200 / 401 / 404 {@code LEARNING_COURSE_NOT_FOUND}. */
    @GetMapping("/intonation-courses/{courseId}")
    ResponseEntity<IntonationCourseResponse> courseDetail(@AuthenticatedUser AppUser user,
                                                          @PathVariable String courseId) {
        return noStore(registry.current().courseResponse(courseId));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
