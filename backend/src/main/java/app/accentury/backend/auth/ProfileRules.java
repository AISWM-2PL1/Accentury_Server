package app.accentury.backend.auth;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * 프로필 항목의 규칙 한 자리 (명세서 §3.10) - 추가 정보 화면의 검증과 IdP 값 정리가 같은 규칙을 쓴다.
 */
final class ProfileRules {

    /**
     * 이메일 형식 - {@code @} 앞뒤가 비지 않고 도메인에 점이 하나 이상 있다. Jakarta {@code @Email}은
     * {@code a@b}도 통과시켜 회신이 불가능한 값이 남는다. 이 이상의 검사(실제 수신 가능 여부)는 하지 않는다.
     */
    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /**
     * 가입 가능한 최소 나이 - 만 14세 미만은 법정대리인 동의가 필요하고(개인정보보호법 제22조의2) 그 절차가
     * 없으므로 가입을 막는다 (명세서 §3.10, {@code AUTH_UNDER_AGE}).
     */
    static final int MINIMUM_AGE = 14;

    private ProfileRules() {
    }

    /** {@code today} 기준 만 나이. */
    static int age(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }
}
