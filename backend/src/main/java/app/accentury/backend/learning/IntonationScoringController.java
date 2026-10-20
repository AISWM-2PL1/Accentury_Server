package app.accentury.backend.learning;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 억양 학습 채점 API (KAN-267, 명세서 §3.19) - 전부 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * <p>
 * 파트와 헤더는 전부 {@code required=false}로 받고 검증은 서비스가 한다 - 레벨테스트 업로드(§3.3)와 같이, 누락을
 * 프레임워크 기본 400이 아니라 명세의 오류 봉투({@code VALIDATION_FAILED})로 돌려주기 위해서다.
 */
@RestController
@RequestMapping("/v0/learning")
class IntonationScoringController {

    private final IntonationScoringService service;

    IntonationScoringController(IntonationScoringService service) {
        this.service = service;
    }

    /** 카드 1건 녹음 업로드 (A-3). 202 / 400 / 401 / 404 / 413 / 415 / 422 / 429 / 503. */
    @PostMapping(path = "/intonation-cards/{cardId}/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<IntonationUploadResponse> upload(@AuthenticatedUser AppUser user,
                                                    @PathVariable String cardId,
                                                    @RequestHeader(name = "Idempotency-Key", required = false)
                                                    @Nullable String idempotencyKey,
                                                    @RequestPart(name = "audio", required = false)
                                                    @Nullable MultipartFile audio,
                                                    @RequestPart(name = "meta", required = false)
                                                    @Nullable String meta) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.upload(user, cardId, idempotencyKey, audio, meta));
    }

    /** 결과 조회 (A-4 폴링, A-5 피드백). 200 / 401 / 403 / 404. 폴링 응답이라 캐시하지 않는다. */
    @GetMapping("/intonation-attempts/{attemptId}")
    ResponseEntity<IntonationAttemptResponse> attempt(@AuthenticatedUser AppUser user,
                                                      @PathVariable String attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(user, attemptId));
    }
}
