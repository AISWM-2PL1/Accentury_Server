package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import app.accentury.backend.common.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사투리 텍스트 번역 API (KAN-266, 명세서 §3.18) - 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * 게스트 계정이 쓸 수 있는 유일한 기능 API다 (KAN-329, §3.21) - 한도와 요청 제한은 로그인 계정과 같이 계정마다 센다.
 * 결과는 입력마다 다르고 사용자의 글이 들어 있어 캐시하지 않는다.
 */
@RestController
class TranslationController {

    private final TranslationService service;
    private final TranslationReportService reportService;
    private final ClientIps clientIps;

    TranslationController(TranslationService service, TranslationReportService reportService, ClientIps clientIps) {
        this.service = service;
        this.reportService = reportService;
        this.clientIps = clientIps;
    }

    /**
     * 200 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID} / 422 {@code TRANSLATION_TOO_LONG},
     * {@code TRANSLATION_REJECTED} / 429 {@code RATE_LIMITED}, {@code TRANSLATION_DAILY_LIMIT_EXCEEDED} /
     * 503 {@code TRANSLATION_UNAVAILABLE}.
     */
    @PostMapping(value = "/v0/translations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TranslationResponse> translate(@AuthenticatedUser(allowGuest = true) AppUser user,
                                                  @RequestBody(required = false) @Nullable TranslationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.translate(user, request));
    }

    /**
     * 번역 결과 신고 (명세서 §3.22, KAN-343). 번역기를 쓰는 계정은 누구나 신고할 수 있어야 하므로 게스트도 받는다.
     * <p>
     * 201 저장됨 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID} / 429 {@code RATE_LIMITED}.
     * 저장이 커밋되면 개발팀 슬랙으로 알림이 나가지만 응답은 그것을 기다리지 않는다.
     */
    @PostMapping(value = "/v0/translations/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TranslationReportResponse> report(
            @AuthenticatedUser(allowGuest = true) AppUser user,
            @RequestBody(required = false) @Nullable TranslationReportRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(reportService.report(user, request, clientIps.resolve(httpRequest)));
    }
}
