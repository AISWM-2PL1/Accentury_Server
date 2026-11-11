package app.accentury.backend.auth;

import app.accentury.backend.session.Region;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                LocalDate.of(1988, 1, 1), "닉네임", "https://k.kakaocdn.net/p.jpg"), NOW.plusSeconds(60));

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
                null, null), NOW);

        assertEquals("a@naver.com", user.email());
        assertNull(user.gender(), "성별은 IdP 값으로 채우지 않는다 - 사용자가 고를 때만 저장한다 (KAN-328)");
        assertFalse(user.isProfileComplete(), "출신지역은 IdP가 주지 않는다 - 추가 정보 화면이 받는다");
    }

    @Test
    void 성별이_없어도_필수_네_항목이_있으면_완료다() {
        AppUser user = new AppUser(IdpProfile.subjectOnly(Provider.GOOGLE, "sub"), "2026-09-24", NOW);

        user.updateProfile("mine@example.com", "이름", LocalDate.of(1999, 3, 2), null, Region.GYEONGNAM, NOW);

        assertNull(user.gender());
        assertTrue(user.isProfileComplete(), "성별은 선택 항목이라 완료 판정에 쓰지 않는다 (KAN-328)");
        assertEquals(NOW, user.profileCompletedAt());
    }

    @Test
    void 성별은_사용자가_고른_값만_남고_가입과_재로그인은_건드리지_않는다() {
        // 카카오와 네이버가 성별을 줘도 IdpProfile에는 자리가 없다 (KAN-328) - 가입 직후에는 성별이 비어 있다.
        IdpProfile kakao = new IdpProfile(Provider.KAKAO, "sub", "k@kakao.com", "이름", LocalDate.of(1999, 3, 2),
                null, null);
        AppUser user = new AppUser(kakao, "2026-09-24", NOW);
        assertNull(user.gender(), "사용자가 고르기 전에는 성별이 저장되지 않는다");

        user.updateProfile("k@kakao.com", "이름", LocalDate.of(1999, 3, 2), Gender.FEMALE, Region.GYEONGNAM, NOW);
        assertFalse(user.fillBlanksFrom(kakao, NOW.plusSeconds(60)));
        assertEquals(Gender.FEMALE, user.gender(), "고른 성별은 재로그인이 바꾸지 않는다");

        user.updateProfile("k@kakao.com", "이름", LocalDate.of(1999, 3, 2), null, Region.GYEONGNAM, NOW);
        assertFalse(user.fillBlanksFrom(kakao, NOW.plusSeconds(120)));
        assertNull(user.gender(), "「선택 안 함」은 재로그인 뒤에도 비어 있다");
        assertTrue(user.isProfileComplete());
    }

    @Test
    void 바뀐_것이_없으면_false다() {
        AppUser user = new AppUser(new IdpProfile(Provider.GOOGLE, "sub", "g@example.com", "이름", null, null, null),
                "2026-09-24", NOW);

        assertFalse(user.fillBlanksFrom(new IdpProfile(Provider.GOOGLE, "sub", "other@example.com", "다른이름",
                null, null, null), NOW));
        assertNotNull(user.email());
    }

    @Test
    void 탈퇴는_개인_정보를_지우고_IdP_사용자_id를_계정_id로_바꾼다() {
        AppUser user = new AppUser(new IdpProfile(Provider.KAKAO, "4242", "k@kakao.com", "이름",
                LocalDate.of(1999, 3, 2), "닉네임", "https://k.kakaocdn.net/p.jpg"), "2026-09-24", NOW);
        user.updateProfile("k@kakao.com", "이름", LocalDate.of(1999, 3, 2), Gender.MALE, Region.GYEONGNAM, NOW);

        user.withdraw(NOW.plusSeconds(60));

        assertEquals("deleted:" + user.id(), user.providerUserId());
        assertNull(user.email());
        assertNull(user.name());
        assertNull(user.birthDate());
        assertNull(user.gender());
        assertNull(user.region());
        assertNull(user.nickname());
        assertNull(user.profileImageUrl());
        assertEquals(NOW.plusSeconds(60), user.deletedAt());
        assertFalse(user.isProfileComplete());
        assertEquals(Provider.KAKAO, user.provider());
        assertEquals(NOW, user.privacyConsentAt(), "동의 기록은 개인 식별 정보가 아니라 남긴다");
    }
}
