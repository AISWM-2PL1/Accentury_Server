package app.accentury.backend.learning;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 억양 학습 콘텐츠 API (KAN-264, 명세서 §3.17) - 전부 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * <p>
 * 조회뿐이라 요청 제한(§2.5)을 걸지 않는다. 응답은 계정 상태에 따라 달라질 수 있으므로(KAN-268의 완료 표시와 추천)
 * 캐시하지 않는다.
 */
@RestController
@RequestMapping("/v0/learning")
class IntonationLearningController {

    private final IntonationLearningRegistry registry;

    IntonationLearningController(IntonationLearningRegistry registry) {
        this.registry = registry;
    }

    /** 코스 목록 (A-1). 200 / 401. */
    @GetMapping("/intonation-courses")
    ResponseEntity<IntonationCourseListResponse> listCourses(@AuthenticatedUser AppUser user) {
        return noStore(registry.current().listResponse());
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
