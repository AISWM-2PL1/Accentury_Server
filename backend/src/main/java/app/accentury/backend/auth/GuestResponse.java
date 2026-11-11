package app.accentury.backend.auth;

/**
 * {@code POST /v0/auth/guest} 200 응답 (명세서 §3.21, KAN-329). 토큰 세 필드는 refresh 응답(§3.12)과 같고 그 뒤의
 * 회전도 같은 API를 쓴다. 로그인 응답의 {@code isNewUser}와 {@code profileStatus}는 싣지 않는다 - 게스트는 부를 때마다
 * 새 계정이고 추가 정보 화면으로 가지 않는다.
 *
 * @param accessToken             Access JWT
 * @param refreshToken            Refresh 원문 - 잃으면 그 게스트 계정으로 돌아갈 방법이 없다
 * @param accessTokenExpiresInSec Access 수명(초)
 * @param user                    계정 - {@code provider}가 {@code GUEST}이고 나머지 항목은 null이다
 */
record GuestResponse(String accessToken, String refreshToken, long accessTokenExpiresInSec, UserView user) {

    @Override
    public String toString() {
        return "GuestResponse[user=" + user + "]";
    }
}
