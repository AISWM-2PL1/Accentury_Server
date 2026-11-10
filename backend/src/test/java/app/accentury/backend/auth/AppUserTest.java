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
        assertEquals(Gender.MALE, user.gender(), "프로필 완료 전에는 성별도 IdP 값으로 채운다");
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
    void 완료된_프로필의_빈_성별은_재로그인이_IdP_값으로_채우지_않는다() {
        // 카카오가 성별을 줬지만 사용자가 추가 정보 화면에서 「선택 안 함」으로 바꿔 저장했다.
        IdpProfile kakao = new IdpProfile(Provider.KAKAO, "sub", "k@kakao.com", "이름", LocalDate.of(1999, 3, 2),
                Gender.MALE, null, null);
        AppUser user = new AppUser(kakao, "2026-09-24", NOW);
        assertEquals(Gender.MALE, user.gender(), "가입 때는 IdP가 준 성별을 채운다");
        user.updateProfile("k@kakao.com", "이름", LocalDate.of(1999, 3, 2), null, Region.GYEONGNAM, NOW);

        boolean changed = user.fillBlanksFrom(kakao, NOW.plusSeconds(60));

        assertFalse(changed, "성별만 비어 있었고 그 값은 채우지 않는다");
        assertNull(user.gender(), "사용자가 고르지 않기로 한 값이 재로그인으로 되돌아가면 안 된다");
        assertTrue(user.isProfileComplete());
    }

    @Test
    void 바뀐_것이_없으면_false다() {
        AppUser user = new AppUser(new IdpProfile(Provider.GOOGLE, "sub", "g@example.com", "이름", null, null, null, null),
                "2026-09-24", NOW);

        assertFalse(user.fillBlanksFrom(new IdpProfile(Provider.GOOGLE, "sub", "other@example.com", "다른이름",
                null, null, null, null), NOW));
        assertNotNull(user.email());
    }

    @Test
    void 탈퇴는_개인_정보를_지우고_IdP_사용자_id를_계정_id로_바꾼다() {
        AppUser user = new AppUser(new IdpProfile(Provider.KAKAO, "4242", "k@kakao.com", "이름",
                LocalDate.of(1999, 3, 2), Gender.MALE, "닉네임", "https://k.kakaocdn.net/p.jpg"), "2026-09-24", NOW);
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
