package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 게스트 계정 발급의 IP당 하루 상한 (KAN-329, 명세서 §2.5, §3.21).
 * <p>
 * 게스트 계정은 만들 때마다 번역 하루 한도(성공 5건, 호출 20번)가 새로 생긴다. 발급을 묶지 않으면 한 사람이 게스트를
 * 계속 만들어 프로젝트 전체가 나눠 쓰는 번역 사업자의 하루 몫을 비울 수 있다. 분당 요청 제한({@code RateLimits})은
 * 메모리에서 세어 backend 태스크 수만큼 풀리고 하루 총량을 막지 못하므로, 번역 한도처럼 Redis에서 센다.
 * <p>
 * 키는 {@code gq:{IP의 SHA-256}:{yyyyMMdd}}(KST 날짜)이고 다음 날 KST 01시에 만료된다. IP 원문을 키에 두지 않는 것은
 * 키 목록만 봐도 접속 IP가 드러나지 않게 하려는 것이다. 상한을 넘은 요청도 카운터를 올린다 - 판정에는 영향이 없고
 * 키와 함께 사라진다.
 * <p>
 * 한 IP를 여러 사람이 나눠 쓰는 곳(통신사, 학교)에서는 정상 사용자도 이 상한에 걸릴 수 있다. 그때 앱은 로그인을
 * 안내한다 - 로그인 계정은 이 상한과 무관하다.
 */
@Component
class GuestQuota {

    private static final Logger log = LoggerFactory.getLogger(GuestQuota.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String KEY = "gq:";
    private static final LocalTime EXPIRE_AT = LocalTime.of(1, 0);

    /** 세기와 만료 설정을 한 번에 한다 - 따로 하면 사이에서 죽은 요청이 만료 없는 키를 남긴다. */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            redis.call('PEXPIREAT', KEYS[1], ARGV[1])
            return n
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    GuestQuota(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    GuestQuota(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * 이 IP의 오늘 발급 한 건을 센다.
     *
     * @throws ApiException 429 {@code AUTH_GUEST_LIMIT_EXCEEDED} - 오늘 상한을 넘었다<br>
     *                      503 {@code AUTH_STORE_UNAVAILABLE} - Redis 장애. 세지 못하면 발급하지 않는다
     */
    void acquire(String clientIp) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        long expireAt = today.plusDays(1).atTime(EXPIRE_AT).atZone(KST).toInstant().toEpochMilli();
        Long used;
        try {
            used = redis.execute(ACQUIRE, List.of(key(clientIp, today)), String.valueOf(expireAt));
        } catch (DataAccessException e) {
            // 연결 실패, 타임아웃, 스크립트 오류가 전부 여기로 온다 (Spring Data Redis의 예외 변환).
            log.error("게스트 발급 상한 저장소(Redis) 호출 실패 - {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
        if (used == null || used > AccenturyProperties.Auth.GUEST_DAILY_LIMIT_PER_IP) {
            throw new ApiException(ErrorCode.AUTH_GUEST_LIMIT_EXCEEDED);
        }
    }

    static String key(String clientIp, LocalDate day) {
        return KEY + RefreshTokens.hash(clientIp) + ":" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
