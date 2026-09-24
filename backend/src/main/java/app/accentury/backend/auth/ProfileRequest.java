package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

/**
 * {@code PUT /v0/users/me/profile} 요청 (명세서 §3.10). 다섯 항목 전부 필수이고 검증은 {@link UserService}가 한다.
 *
 * @param email     이메일 - 형식 검사, 254자 이하
 * @param name      이름 - 앞뒤 공백을 뺀 1~50자
 * @param birthDate {@code YYYY-MM-DD} - 오늘(Asia/Seoul) 이전, 만 14세 이상
 * @param gender    {@code MALE | FEMALE}
 * @param region    출신지역 코드 10개 중 하나 (§3.1, {@code Region})
 */
record ProfileRequest(@Nullable String email,
                      @Nullable String name,
                      @Nullable String birthDate,
                      @Nullable String gender,
                      @Nullable String region) {

    /** 값을 찍지 않는다 - 전부 개인 정보다 (§2.6). */
    @Override
    public String toString() {
        return "ProfileRequest[]";
    }
}
