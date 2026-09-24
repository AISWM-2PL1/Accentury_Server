package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 응답의 {@code user} (명세서 §3.9, §3.10, §3.11). 로그인, 프로필 완료, 내 정보가 같은 모양을 쓴다.
 * 비어 있는 항목은 null로 나간다 - 클라이언트가 추가 정보 화면을 미리 채우는 데 쓴다 (KAN-224).
 */
record UserView(UUID id, Provider provider, @Nullable String email, @Nullable String name,
                @Nullable LocalDate birthDate, @Nullable Gender gender, @Nullable String region,
                @Nullable String nickname, @Nullable String profileImageUrl) {

    static UserView of(AppUser user) {
        return new UserView(user.id(), user.provider(), user.email(), user.name(), user.birthDate(),
                user.gender(), user.region() != null ? user.region().name() : null,
                user.nickname(), user.profileImageUrl());
    }

    /** id만 찍는다 - 나머지는 개인 정보다 (§2.6). */
    @Override
    public String toString() {
        return "UserView[id=" + id + "]";
    }
}
