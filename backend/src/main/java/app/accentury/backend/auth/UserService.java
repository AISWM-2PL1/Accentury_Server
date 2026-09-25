package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.session.Region;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 추가 정보 입력과 내 정보 (KAN-223, 명세서 §3.10, §3.11).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * 만 나이와 "오늘"의 기준 - 서비스 대상이 한국이다. UTC로 세면 한국 자정부터 오전 9시까지 생일인 사람이
     * 하루 늦게 나이를 먹는다.
     */
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AppUserRepository users;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    UserService(AppUserRepository users, TransactionTemplate transactionTemplate) {
        this(users, transactionTemplate, Clock.system(ZONE));
    }

    UserService(AppUserRepository users, TransactionTemplate transactionTemplate, Clock clock) {
        this.users = users;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** 내 계정 (§3.11). */
    MeResponse me(AppUser user) {
        return MeResponse.of(user);
    }

    /**
     * 다섯 항목을 검증해 저장한다 (§3.10). 거절된 값은 저장하지 않는다 - 만 14세 미만의 생년월일도 남기지 않는다.
     *
     * @throws ApiException 400 {@code VALIDATION_FAILED} 또는 {@code AUTH_UNDER_AGE} / 401 {@code AUTH_TOKEN_INVALID}
     */
    MeResponse updateProfile(AppUser user, @Nullable ProfileRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 본문이 필요합니다.");
        }
        String email = email(request.email());
        String name = name(request.name());
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate birthDate = birthDate(request.birthDate(), today);
        Gender gender = gender(request.gender());
        Region region = region(request.region());
        if (ProfileRules.age(birthDate, today) < ProfileRules.MINIMUM_AGE) {
            throw new ApiException(ErrorCode.AUTH_UNDER_AGE);
        }

        AppUser saved = transactionTemplate.execute(tx -> {
            // 인자로 받은 계정은 인증 단계에서 읽은 것이라 트랜잭션 밖이다 - 잠금과 함께 다시 읽어 고친다.
            AppUser locked = users.lockActive(user.id())
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
            locked.updateProfile(email, name, birthDate, gender, region, Instant.now(clock));
            return locked;
        });
        if (saved == null) {
            throw new IllegalStateException("프로필 트랜잭션이 결과 없이 끝났다");
        }
        // 값은 남기지 않는다 - 완료 여부만 (§2.6).
        log.info("프로필 저장 userId={} status={}", saved.id(), ProfileStatus.of(saved));
        return MeResponse.of(saved);
    }

    private static String email(@Nullable String raw) {
        String value = IdpProfile.blankToNull(raw);
        if (value == null || value.length() > IdpProfile.EMAIL_MAX || !ProfileRules.EMAIL.matcher(value).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이메일 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static String name(@Nullable String raw) {
        String value = IdpProfile.blankToNull(raw);
        if (value == null || value.codePointCount(0, value.length()) > IdpProfile.NAME_MAX) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이름은 1~50자여야 합니다.");
        }
        return value;
    }

    private static LocalDate birthDate(@Nullable String raw, LocalDate today) {
        if (raw == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "생년월일이 필요합니다.");
        }
        LocalDate value;
        try {
            // ISO_LOCAL_DATE는 YYYY-MM-DD만 받는다 - 2월 30일 같은 없는 날짜도 여기서 거절된다.
            value = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "생년월일은 YYYY-MM-DD 형식이어야 합니다.");
        }
        if (!value.isBefore(today)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "생년월일은 오늘 이전이어야 합니다.");
        }
        return value;
    }

    private static Gender gender(@Nullable String raw) {
        if (raw != null) {
            for (Gender gender : Gender.values()) {
                if (gender.name().equals(raw)) {
                    return gender;
                }
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "gender는 MALE, FEMALE 중 하나여야 합니다.");
    }

    private static Region region(@Nullable String raw) {
        // 세션 생성과 같은 검증이다 (§3.1) - 다만 여기서는 필수라 빈 값도 400이다.
        Region region = Region.fromRequest(raw);
        if (region == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "출신지역이 필요합니다.");
        }
        return region;
    }
}
