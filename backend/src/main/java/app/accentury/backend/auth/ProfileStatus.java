package app.accentury.backend.auth;

/**
 * 프로필 완료 여부 (명세서 §3.9 {@code profileStatus}). INCOMPLETE면 클라이언트는 추가 정보 화면(§3.10)으로 간다.
 */
public enum ProfileStatus {
    COMPLETE, INCOMPLETE;

    static ProfileStatus of(AppUser user) {
        return user.isProfileComplete() ? COMPLETE : INCOMPLETE;
    }
}
