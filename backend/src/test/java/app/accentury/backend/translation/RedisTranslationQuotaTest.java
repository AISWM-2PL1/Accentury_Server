package app.accentury.backend.translation;

import app.accentury.backend.IntegrationTest;
import app.accentury.backend.RedisTestcontainer;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 번역 일일 한도의 Redis 카운터 (KAN-266, 명세서 §3.18) - 한국 시각 자정 경계, 동시 요청, 만료 시각을 실제 Redis에서 본다.
 */
@Import(RedisTestcontainer.class)
class RedisTranslationQuotaTest extends IntegrationTest {

    private static final int LIMIT = AccenturyProperties.Translation.DAILY_LIMIT;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void 다섯_칸을_잡으면_여섯_번째는_소진이고_돌려주면_다시_잡힌다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        TranslationQuota.Slot last = null;
        for (int used = 1; used <= LIMIT; used++) {
            last = quota.acquire(user);
            assertEquals(used, last.used());
        }
        assertEquals(0, last.remaining());
        assertThrows(TranslationQuota.Exhausted.class, () -> quota.acquire(user));

        quota.release(last);
        assertEquals(LIMIT, quota.acquire(user).used());
    }

    @Test
    void 한국_시각_자정에_새_하루가_된다() {
        UUID user = UUID.randomUUID();
        // UTC로는 같은 날(15:00 전후)이지만 KST로는 23:59와 다음 날 00:00이다.
        RedisTranslationQuota beforeMidnight = quotaAt(today().atTime(23, 59));
        RedisTranslationQuota afterMidnight = quotaAt(today().plusDays(1).atStartOfDay());
        for (int i = 0; i < LIMIT; i++) {
            beforeMidnight.acquire(user);
        }
        assertThrows(TranslationQuota.Exhausted.class, () -> beforeMidnight.acquire(user));

        assertEquals(1, afterMidnight.acquire(user).used());
    }

    @Test
    void 자정_전에_잡은_칸은_자정_뒤에_돌려줘도_그날_카운터로_간다() {
        UUID user = UUID.randomUUID();
        RedisTranslationQuota beforeMidnight = quotaAt(today().atTime(23, 59, 59));
        TranslationQuota.Slot slot = beforeMidnight.acquire(user);
        RedisTranslationQuota afterMidnight = quotaAt(today().plusDays(1).atTime(0, 0, 1));
        TranslationQuota.Slot today = afterMidnight.acquire(user);

        afterMidnight.release(slot);

        assertEquals("0", redis.opsForValue().get(slot.key()));
        assertEquals("1", redis.opsForValue().get(today.key()), "새 날의 카운터는 그대로다");
    }

    @Test
    void 남은_수는_성공한_때의_카운터로_계산한다() {
        // 칸 4와 5를 잡은 두 요청 가운데 4가 실패해 돌려준 뒤 5가 성공하면, 5가 잡을 때의 값(0)이 아니라 1이 남는다.
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        for (int i = 0; i < LIMIT - 2; i++) {
            quota.acquire(user);
        }
        TranslationQuota.Slot fourth = quota.acquire(user);
        TranslationQuota.Slot fifth = quota.acquire(user);
        assertEquals(0, fifth.remaining());

        quota.release(fourth);

        assertEquals(1, quota.remaining(fifth));
    }

    @Test
    void 돌려줄_칸이_없으면_0_아래로_내려가지_않는다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        TranslationQuota.Slot slot = quota.acquire(UUID.randomUUID());
        quota.release(slot);
        quota.release(slot);
        assertEquals("0", redis.opsForValue().get(slot.key()));
    }

    @Test
    void 카운터는_다음_날_한국_시각_01시에_만료된다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        TranslationQuota.Slot slot = quota.acquire(UUID.randomUUID());

        long expected = today().plusDays(1).atTime(1, 0).atZone(KST).toInstant().toEpochMilli()
                - System.currentTimeMillis();
        Long ttl = redis.getExpire(slot.key(), TimeUnit.MILLISECONDS);
        assertTrue(Math.abs(expected - ttl) < 5_000, "만료까지 남은 시간 " + ttl + "ms, 기대 " + expected + "ms");
    }

    @Test
    void 동시에_몰려도_다섯_칸만_잡힌다() throws Exception {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        Callable<Boolean> attempt = () -> {
            try {
                quota.acquire(user);
                return true;
            } catch (TranslationQuota.Exhausted e) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = pool.invokeAll(Collections.nCopies(20, attempt));
            long acquired = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    acquired++;
                }
            }
            assertEquals(LIMIT, acquired);
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * 시계를 KST 시각에 고정한다. 날짜는 실행 당일 기준이다 - 지난 날짜로 고정하면 만료 시각({@code PEXPIREAT})이 이미
     * 지나 Redis가 키를 바로 지운다.
     */
    private RedisTranslationQuota quotaAt(LocalDateTime kst) {
        return new RedisTranslationQuota(redis, Clock.fixed(kst.atZone(KST).toInstant(), ZoneOffset.UTC));
    }

    private static LocalDate today() {
        return LocalDate.now(KST);
    }
}
