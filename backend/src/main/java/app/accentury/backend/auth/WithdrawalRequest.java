package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/users/me/withdrawal}(§3.14)의 요청 본문 - 본문 자체가 선택이다.
 *
 * @param appleAuthorizationCode 애플 계정만 - 탈퇴 직전 애플 로그인으로 받은 authorization code. 서버가 교환해 애플
 *                               토큰을 revoke한다 ({@link AppleTokenRevoker}). 다른 IdP 계정이 보내면 무시한다.
 */
record WithdrawalRequest(@Nullable String appleAuthorizationCode) {

    @Override
    public String toString() {
        return "WithdrawalRequest[]";
    }
}
