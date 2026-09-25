package app.accentury.backend.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IdP가 준 값의 정리 (KAN-223) - 열 길이를 넘거나 형식이 이상한 보조 정보 때문에 로그인이 막히면 안 된다.
 */
class IdpProfileTest {

    @Test
    void 긴_이름은_글자_단위로_자르고_이상한_이메일과_긴_URL은_버린다() {
        IdpProfile profile = new IdpProfile(Provider.GOOGLE, "sub",
                "no-at-sign", "가".repeat(60) + "😀", null, null, "  닉  ", "https://x/" + "a".repeat(1100));

        assertNull(profile.email());
        assertEquals(50, profile.name().codePointCount(0, profile.name().length()));
        assertEquals("닉", profile.nickname());
        assertNull(profile.profileImageUrl());
    }

    @Test
    void 이모지를_반으로_자르지_않는다() {
        IdpProfile profile = new IdpProfile(Provider.KAKAO, "sub", null, "😀".repeat(51), null, null, null, null);

        assertEquals("😀".repeat(50), profile.name());
    }

    @Test
    void subject가_비면_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> IdpProfile.subjectOnly(Provider.NAVER, " "));
    }
}
