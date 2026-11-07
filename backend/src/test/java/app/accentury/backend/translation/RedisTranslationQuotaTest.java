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
    private static final int CALL_LIMIT = AccenturyProperties.Translation.DAILY_CALL_LIMIT;
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

    @Test
    void 돌려준_칸의_호출_수는_남아_상한_다음은_소진이고_성공_칸은_그대로다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        TranslationQuota.Slot last = null;
        for (int i = 0; i < CALL_LIMIT; i++) {
            last = quota.acquire(user);
            quota.release(last);
        }

        assertThrows(TranslationQuota.Exhausted.class, () -> quota.acquire(user));

        assertEquals("0", redis.opsForValue().get(last.key()), "거절 호출은 성공 칸을 쓰지 않는다");
        assertEquals(String.valueOf(CALL_LIMIT), redis.opsForValue().get(callKey(last)),
                "상한에 걸린 요청은 LLM을 부르지 않으니 호출 수에 남지 않는다");
    }

    @Test
    void 호출_상한에_걸리면_성공_칸을_되돌린다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        TranslationQuota.Slot held = quota.acquire(user);
        for (int i = 1; i < CALL_LIMIT; i++) {
            quota.release(quota.acquire(user));
        }

        assertThrows(TranslationQuota.Exhausted.class, () -> quota.acquire(user));

        assertEquals("1", redis.opsForValue().get(held.key()), "먼저 잡은 칸만 남는다");
    }

    @Test
    void 성공_칸이_없어_거절된_요청은_호출_수를_쓰지_않는다() {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        TranslationQuota.Slot last = null;
        for (int i = 0; i < LIMIT; i++) {
            last = quota.acquire(user);
        }

        assertThrows(TranslationQuota.Exhausted.class, () -> quota.acquire(user));

        assertEquals(String.valueOf(LIMIT), redis.opsForValue().get(callKey(last)));
    }

    @Test
    void 호출_수도_한국_시각_자정에_새_하루가_되고_같은_시각에_만료된다() {
        UUID user = UUID.randomUUID();
        RedisTranslationQuota beforeMidnight = quotaAt(today().atTime(23, 59));
        RedisTranslationQuota afterMidnight = quotaAt(today().plusDays(1).atStartOfDay());
        TranslationQuota.Slot last = null;
        for (int i = 0; i < CALL_LIMIT; i++) {
            last = beforeMidnight.acquire(user);
            beforeMidnight.release(last);
        }
        assertThrows(TranslationQuota.Exhausted.class, () -> beforeMidnight.acquire(user));

        assertEquals(1, afterMidnight.acquire(user).used());

        Long slotTtl = redis.getExpire(last.key(), TimeUnit.MILLISECONDS);
        Long callTtl = redis.getExpire(callKey(last), TimeUnit.MILLISECONDS);
        assertTrue(Math.abs(slotTtl - callTtl) < 5_000, "성공 칸 " + slotTtl + "ms, 호출 수 " + callTtl + "ms");
    }

    @Test
    void 동시에_몰려도_호출_수는_상한을_넘지_않는다() throws Exception {
        RedisTranslationQuota quota = quotaAt(today().atTime(12, 0));
        UUID user = UUID.randomUUID();
        Callable<Boolean> attempt = () -> {
            try {
                quota.release(quota.acquire(user));
                return true;
            } catch (TranslationQuota.Exhausted e) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            long passed = 0;
            for (Future<Boolean> result : pool.invokeAll(Collections.nCopies(CALL_LIMIT * 3, attempt))) {
                if (result.get()) {
                    passed++;
                }
            }
            assertEquals(CALL_LIMIT, passed);
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /** 칸의 키에서 같은 날의 호출 수 키를 만든다. */
    private static String callKey(TranslationQuota.Slot slot) {
        return slot.key().replaceFirst("^trq:", "trc:");
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
