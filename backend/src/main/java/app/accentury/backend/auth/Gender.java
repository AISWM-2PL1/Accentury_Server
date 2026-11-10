package app.accentury.backend.auth;

/**
 * 성별 두 값 (명세서 §3.10). 선택 항목이라 계정에 없을 수 있다 (KAN-328). 저장 값은 이름 그대로다
 * ({@code app_user.gender}).
 */
public enum Gender {
    MALE, FEMALE
}
