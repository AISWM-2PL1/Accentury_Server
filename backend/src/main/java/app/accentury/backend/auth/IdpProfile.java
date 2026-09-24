package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * IdP가 확인해 준 사용자 (명세서 §3.9 IdP별 검증 표의 "받는 정보").
 * <p>
 * {@code subject}만 필수이고 나머지는 IdP가 주지 않거나 사용자가 동의하지 않으면 null이다 - 비어 있는 항목은
 * 추가 정보 화면(§3.10)이 채운다. 값은 여기서 {@code app_user} 열 길이에 맞게 정리된다: 이름과 닉네임은 길이
 * 상한에서 자르고(글자 단위), 형식이 이상하거나 너무 긴 이메일과 이미지 URL은 버린다. IdP가 준 값 때문에 저장이
 * 실패하면 로그인 자체가 막히는데, 그 값들은 없어도 되는 것들이다.
 *
 * @param provider        어느 IdP인가
 * @param subject         IdP의 사용자 id (구글과 애플 {@code sub}, 카카오와 네이버 {@code id})
 * @param email           이메일 - 구글은 {@code email_verified}, 카카오는 유효와 인증이 둘 다 참일 때만 온다
 * @param name            이름 (애플은 요청 본문 {@code user.name})
 * @param birthDate       생년월일 - 출생연도와 생일이 둘 다 있을 때만(카카오는 양력일 때만) 온다
 * @param gender          성별
 * @param nickname        닉네임 (보조 정보)
 * @param profileImageUrl 프로필 이미지 URL (보조 정보)
 */
public record IdpProfile(Provider provider, String subject,
                         @Nullable String email, @Nullable String name, @Nullable LocalDate birthDate,
                         @Nullable Gender gender, @Nullable String nickname, @Nullable String profileImageUrl) {

    static final int SUBJECT_MAX = 255;
    static final int EMAIL_MAX = 254;
    static final int NAME_MAX = 50;
    static final int NICKNAME_MAX = 100;
    static final int IMAGE_URL_MAX = 1024;

    public IdpProfile {
        if (subject.isBlank() || subject.length() > SUBJECT_MAX) {
            // IdP가 식별자를 안 줬다는 것은 검증이 통과하지 않은 것과 같다 - 호출 쪽이 먼저 걸러야 한다.
            throw new IllegalArgumentException("IdP subject가 비었거나 너무 길다: " + subject.length() + "자");
        }
        email = email(email);
        name = truncate(name, NAME_MAX);
        nickname = truncate(nickname, NICKNAME_MAX);
        profileImageUrl = blankToNull(profileImageUrl);
        if (profileImageUrl != null && profileImageUrl.length() > IMAGE_URL_MAX) {
            profileImageUrl = null;
        }
    }

    /** 정보 없이 식별자만 있는 프로필 - 가짜 IdP(§3.9)와 정보 동의를 하나도 안 한 사용자가 이 모양이다. */
    static IdpProfile subjectOnly(Provider provider, String subject) {
        return new IdpProfile(provider, subject, null, null, null, null, null, null);
    }

    private static @Nullable String email(@Nullable String raw) {
        String value = blankToNull(raw);
        if (value == null || value.length() > EMAIL_MAX || !ProfileRules.EMAIL.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    /** 앞뒤 공백을 빼고 {@code max} 글자(코드 포인트)에서 자른다 - varchar(n)은 글자 수로 센다. */
    static @Nullable String truncate(@Nullable String raw, int max) {
        String value = blankToNull(raw);
        if (value == null || value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max)).strip();
    }

    static @Nullable String blankToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        return value.isEmpty() ? null : value;
    }
}
