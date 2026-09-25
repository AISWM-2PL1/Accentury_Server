package app.accentury.backend.auth;

/**
 * {@code POST /v0/auth/login} 200 응답 (명세서 §3.9).
 *
 * @param accessToken             Access JWT
 * @param refreshToken            Refresh 원문 - 클라이언트가 안전한 저장소에 둔다 (KAN-224)
 * @param accessTokenExpiresInSec Access 수명(초)
 * @param isNewUser               이번 호출로 가입했는가
 * @param profileStatus           INCOMPLETE면 추가 정보 화면으로
 * @param user                    계정
 */
record LoginResponse(String accessToken, String refreshToken, long accessTokenExpiresInSec,
                     boolean isNewUser, ProfileStatus profileStatus, UserView user) {

    @Override
    public String toString() {
        return "LoginResponse[user=" + user + "]";
    }
}
