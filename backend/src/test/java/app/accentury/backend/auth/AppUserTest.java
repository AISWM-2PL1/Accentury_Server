package app.accentury.backend.auth;

import app.accentury.backend.session.Region;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 재로그인의 빈 열 채우기 (명세서 §3.9) - 사용자가 입력한 값은 IdP 값이 덮지 않는다. 가짜 IdP는 식별자만 주므로 API
 * 테스트로는 이 경로를 못 탄다 (PR #2 리뷰).
 */
class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void 재로그인은_사용자가_입력한_값을_덮지_않고_빈_항목만_채운다() {
        AppUser user = new AppUser(IdpProfile.subjectOnly(Provider.KAKAO, "sub"), "2026-09-24", NOW);
        user.updateProfile("mine@example.com", "내가고친이름", LocalDate.of(1999, 3, 2), Gender.FEMALE, Region.SEOUL, NOW);

        boolean changed = user.fillBlanksFrom(new IdpProfile(Provider.KAKAO, "sub", "idp@kakao.com", "카카오이름",
                LocalDate.of(1988, 1, 1), Gender.MALE, "닉네임", "https://k.kakaocdn.net/p.jpg"), NOW.plusSeconds(60));

        assertTrue(changed, "비어 있던 닉네임과 이미지는 채워진다");
        assertEquals("mine@example.com", user.email());
        assertEquals("내가고친이름", user.name());
        assertEquals(LocalDate.of(1999, 3, 2), user.birthDate());
        assertEquals(Gender.FEMALE, user.gender());
        assertEquals("닉네임", user.nickname());
        assertEquals("https://k.kakaocdn.net/p.jpg", user.profileImageUrl());
        assertEquals(NOW, user.profileCompletedAt(), "완료 시각은 처음 것이다");
    }

    @Test
    void 빈_항목을_IdP가_채워도_출신지역이_없으면_완료가_아니다() {
        AppUser user = new AppUser(IdpProfile.subjectOnly(Provider.NAVER, "sub"), "2026-09-24", NOW);

        user.fillBlanksFrom(new IdpProfile(Provider.NAVER, "sub", "a@naver.com", "이름", LocalDate.of(1999, 3, 2),
                Gender.MALE, null, null), NOW);

        assertEquals("a@naver.com", user.email());
        assertFalse(user.isProfileComplete(), "출신지역은 IdP가 주지 않는다 - 추가 정보 화면이 받는다");
    }

    @Test
    void 바뀐_것이_없으면_false다() {
        AppUser user = new AppUser(new IdpProfile(Provider.GOOGLE, "sub", "g@example.com", "이름", null, null, null, null),
                "2026-09-24", NOW);

        assertFalse(user.fillBlanksFrom(new IdpProfile(Provider.GOOGLE, "sub", "other@example.com", "다른이름",
                null, null, null, null), NOW));
        assertNotNull(user.email());
    }
}
