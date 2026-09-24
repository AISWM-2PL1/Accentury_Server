package app.accentury.backend.auth;

import app.accentury.backend.common.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인, refresh, 로그아웃 (KAN-223, 명세서 §3.9, §3.12, §3.13).
 * <p>
 * 토큰이 든 응답은 어떤 캐시에도 남기지 않는다 ({@code no-store}).
 */
@RestController
@RequestMapping("/v0/auth")
class AuthController {

    private final AuthService authService;
    private final ClientIps clientIps;

    AuthController(AuthService authService, ClientIps clientIps) {
        this.authService = authService;
        this.clientIps = clientIps;
    }

    /**
     * 소셜 로그인 = 가입 겸용 (§3.9). 인증 불필요.
     * <p>
     * 200 토큰 쌍과 계정 / 400 {@code VALIDATION_FAILED}, {@code AUTH_CONSENT_REQUIRED} /
     * 401 {@code AUTH_IDP_TOKEN_INVALID} / 429 {@code RATE_LIMITED} / 502 {@code AUTH_IDP_UNAVAILABLE} /
     * 503 {@code AUTH_STORE_UNAVAILABLE}.
     */
    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@RequestBody(required = false) @Nullable LoginRequest request,
                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request, clientIps.resolve(httpRequest)));
    }

    /**
     * Refresh 회전 (§3.12). 인증 불필요 - Refresh 자체가 자격 증명이다.
     * <p>
     * 200 새 쌍 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_REFRESH_INVALID}, {@code AUTH_REFRESH_REUSED} /
     * 429 {@code RATE_LIMITED} / 503 {@code AUTH_STORE_UNAVAILABLE}.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) @Nullable RefreshTokenRequest request,
                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.refresh(request, clientIps.resolve(httpRequest)));
    }

    /**
     * 로그아웃 (§3.13) - Access 토큰 필수.
     * <p>
     * 204 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID} / 503 {@code AUTH_STORE_UNAVAILABLE}.
     */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticatedUser AppUser user,
                                @RequestBody(required = false) @Nullable RefreshTokenRequest request) {
        authService.logout(user, request);
        return ResponseEntity.noContent().build();
    }
}
