package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
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
import java.util.UUID;

/**
 * 번역 일일 한도의 Redis 카운터 (KAN-266, 명세서 §2.5, §3.18).
 * <p>
 * 다른 요청 제한({@code RateLimits})과 달리 메모리가 아니라 Redis에서 센다 - 메모리로 세면 backend 태스크 수만큼 풀리고
 * 재배포 때 0으로 돌아가 하루 5건이 지켜지지 않는다. 키는 {@code trq:{userId}:{yyyyMMdd}}(KST 날짜)이고 다음 날 KST
 * 01시에 만료된다 - 하루가 바뀌면 새 키로 세므로 만료는 정리용이고, 1시간 여유는 자정 직전에 잡은 칸을 자정 뒤에 돌려줄 때
 * 키가 이미 사라져 있지 않게 한다.
 */
@Component
class RedisTranslationQuota implements TranslationQuota {

    private static final Logger log = LoggerFactory.getLogger(RedisTranslationQuota.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY = "trq:";
    private static final LocalTime EXPIRE_AT = LocalTime.of(1, 0);

    /** 한 칸 잡기 - 넘으면 되돌리고 0. 판정과 쓰기가 스크립트 하나라 동시 요청이 함께 통과하지 못한다. */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            if n > tonumber(ARGV[1]) then
              redis.call('DECR', KEYS[1])
              return 0
            end
            return n
            """, Long.class);

    /** 한 칸 돌려주기 - 0 아래로 내려가지 않는다. */
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local n = tonumber(redis.call('GET', KEYS[1]) or '0')
            if n > 0 then
              return redis.call('DECR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    RedisTranslationQuota(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    RedisTranslationQuota(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Slot acquire(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        String key = KEY + userId + ":" + today.format(DateTimeFormatter.BASIC_ISO_DATE);
        long expireAt = today.plusDays(1).atTime(EXPIRE_AT).atZone(KST).toInstant().toEpochMilli();
        Long used;
        try {
            used = redis.execute(ACQUIRE, List.of(key),
                    String.valueOf(AccenturyProperties.Translation.DAILY_LIMIT), String.valueOf(expireAt));
        } catch (DataAccessException e) {
            // 연결 실패, 타임아웃, 스크립트 오류가 전부 여기로 온다 (Spring Data Redis의 예외 변환).
            log.error("번역 한도 저장소(Redis) 호출 실패 - {}", e.getClass().getSimpleName());
            throw new StoreUnavailable(e.getClass().getSimpleName());
        }
        if (used == null || used == 0) {
            throw new Exhausted();
        }
        return new Slot(key, used.intValue());
    }

    @Override
    public void release(Slot slot) {
        try {
            redis.execute(RELEASE, List.of(slot.key()));
        } catch (DataAccessException e) {
            log.warn("번역 한도 칸 반환 실패 - 그 칸은 자정까지 잃는다 ({})", e.getClass().getSimpleName());
        }
    }

    @Override
    public int remaining(Slot slot) {
        String used;
        try {
            used = redis.opsForValue().get(slot.key());
        } catch (DataAccessException e) {
            log.warn("번역 한도 다시 읽기 실패 - 잡을 때의 값으로 답한다 ({})", e.getClass().getSimpleName());
            return slot.remaining();
        }
        return used != null ? Slot.remainingAfter(Long.parseLong(used)) : slot.remaining();
    }
}
