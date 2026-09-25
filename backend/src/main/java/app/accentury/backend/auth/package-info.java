/**
 * 앱 계정 인증 (KAN-223, SRS M1).
 * <p>
 * 앱(Android, iOS)이 네이티브 SDK로 받은 IdP 토큰을 서버가 IdP에 직접 확인한 뒤 우리 토큰 쌍(Access JWT +
 * Refresh)으로 바꿔 준다. 웹은 익명 유지이고 이 패키지를 쓰지 않는다 (API 명세서 §2.1, §3.9~§3.13).
 * <p>
 * Spring Security는 들이지 않는다. 보호할 경로가 넷(프로필, 내 정보, 로그아웃, 세션 생성의 계정 귀속)뿐이라
 * 필터 체인보다 컨트롤러 인자({@link AuthenticatedUser}) 하나가 싸다 - 관리자 API의 {@code AdminAuth}와 같은 판단이다.
 * <p>
 * 저장소는 둘이다. 계정({@link AppUser})은 PostgreSQL이고, Refresh 토큰({@link RefreshTokens})은 Redis다.
 * Redis가 죽어도 익명 응시는 살아 있어야 하므로(NFR-AV-02) Redis 장애는 로그인, refresh, 로그아웃의
 * 503 {@code AUTH_STORE_UNAVAILABLE}로만 드러난다.
 * <p>
 * 로그에는 이메일, 이름, 생년월일, IdP 토큰, JWT, Refresh 원문을 남기지 않는다 (§2.6, NFR-SC-07).
 */
@NullMarked
package app.accentury.backend.auth;

import org.jspecify.annotations.NullMarked;
