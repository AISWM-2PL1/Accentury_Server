package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 (KAN-223, KAN-241, 명세서 §3.10, §3.11, §3.14) - 전부 Access 토큰 필수다. 개인 정보가 든 응답이라 캐시하지 않는다.
 */
@RestController
@RequestMapping("/v0/users/me")
class UserController {

    private final UserService userService;
    private final WithdrawalService withdrawalService;

    UserController(UserService userService, WithdrawalService withdrawalService) {
        this.userService = userService;
        this.withdrawalService = withdrawalService;
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

    /**
     * 음성 저장(학습 활용) 선택 동의 (§3.15, KAN-269). 본문의 {@code version}이 게시 중인 동의 버전과 같아야 한다.
     * <p>
     * 200 / 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID}.
     */
    @PutMapping("/voice-consent")
    ResponseEntity<MeResponse> consentToVoice(@AuthenticatedUser AppUser user,
                                              @RequestBody(required = false) @Nullable VoiceConsentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userService.consentToVoice(user, request));
    }

    /**
     * 음성 저장 동의 철회 (§3.15, KAN-269). 이 뒤의 업로드부터 저장되지 않는다. 동의한 적이 없어도 200이다.
     * <p>
     * 200 / 401 {@code AUTH_TOKEN_INVALID}.
     */
    @DeleteMapping("/voice-consent")
    ResponseEntity<MeResponse> withdrawVoiceConsent(@AuthenticatedUser AppUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userService.withdrawVoiceConsent(user));
    }

    /**
     * 회원 탈퇴 (§3.14). 계정의 개인 정보를 즉시 파기하고 모든 기기의 로그인을 끊는다. 본문은 선택이고 애플 계정만
     * {@code appleAuthorizationCode}를 싣는다.
     * <p>
     * 204 / 401 {@code AUTH_TOKEN_INVALID}. 애플 revoke와 Refresh 폐기의 실패는 응답을 바꾸지 않는다 ({@link WithdrawalService}).
     */
    @PostMapping("/withdrawal")
    ResponseEntity<Void> withdraw(@AuthenticatedUser AppUser user,
                                  @RequestBody(required = false) @Nullable WithdrawalRequest request) {
        withdrawalService.withdraw(user, request);
        return ResponseEntity.noContent().build();
    }
}
