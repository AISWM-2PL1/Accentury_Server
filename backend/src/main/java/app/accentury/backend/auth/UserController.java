package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 (KAN-223, 명세서 §3.10, §3.11) - 둘 다 Access 토큰 필수다. 개인 정보가 든 응답이라 캐시하지 않는다.
 */
@RestController
@RequestMapping("/v0/users/me")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 내 계정과 프로필 완료 여부 (§3.11). 앱이 재실행 때 추가 정보 화면으로 갈지 정하는 데 쓴다.
     * <p>
     * 200 / 401 {@code AUTH_TOKEN_INVALID}.
     */
    @GetMapping
    ResponseEntity<MeResponse> me(@AuthenticatedUser AppUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.me(user));
    }

    /**
     * 추가 정보 입력 = 프로필 완료 (§3.10). 이미 완료된 프로필에 다시 부르면 값을 갱신한다 (멱등).
     * <p>
     * 200 / 400 {@code VALIDATION_FAILED}, {@code AUTH_UNDER_AGE} / 401 {@code AUTH_TOKEN_INVALID}.
     */
    @PutMapping("/profile")
    ResponseEntity<MeResponse> updateProfile(@AuthenticatedUser AppUser user,
                                             @RequestBody(required = false) @Nullable ProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userService.updateProfile(user, request));
    }
}
