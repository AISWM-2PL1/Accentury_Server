package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/auth/guest} 요청 (명세서 §3.21, KAN-329). 로그인의 가입 동의와 같은 두 필드다 - 앱은 「로그인 없이
 * 둘러보기」를 누를 때 동의를 받고 이 요청을 보낸다.
 *
 * @param privacyConsent       개인정보 수집 이용 동의 - true여야 한다
 * @param privacyPolicyVersion 동의한 방침 버전 - 서버가 게시 중인 버전과 정확히 같아야 한다 (KAN-240)
 */
record GuestRequest(@Nullable Boolean privacyConsent, @Nullable String privacyPolicyVersion) {
}
