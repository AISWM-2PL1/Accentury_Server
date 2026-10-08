package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AuthenticatedUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사투리 텍스트 번역 API (KAN-266, 명세서 §3.18) - 계정 Access 토큰 필수다 ({@link AuthenticatedUser}, 없으면 401).
 * 결과는 입력마다 다르고 사용자의 글이 들어 있어 캐시하지 않는다.
 */
@RestController
class TranslationController {

    private final TranslationService service;

    TranslationController(TranslationService service) {
        this.service = service;
    }

    /**
     * 200 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID} / 422 {@code TRANSLATION_TOO_LONG},
     * {@code TRANSLATION_REJECTED} / 503 {@code TRANSLATION_UNAVAILABLE}.
     */
    @PostMapping(value = "/v0/translations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TranslationResponse> translate(@AuthenticatedUser AppUser user,
                                                  @RequestBody(required = false) @Nullable TranslationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.translate(user, request));
    }
}
