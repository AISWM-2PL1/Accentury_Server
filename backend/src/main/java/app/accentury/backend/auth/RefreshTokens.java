package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Refresh 토큰 = 불투명 난수 + Redis 회전 저장소 (KAN-223, 명세서 §2.1, §3.12, §3.13).
 * <p>
 * 원문({@code rt_} + base64url 256bit)은 클라이언트만 갖고 서버는 SHA-256 해시만 둔다 - 세션 토큰
 * ({@code SessionTokens})과 같은 규칙이다. 접두사 {@code rt_}는 로그 마스킹이 원문을 알아보는 표지이기도 하다.
 *
 * <h4>Redis 자료 구조</h4>
 * <ul>
 *   <li>{@code rt:{해시}} - 해시 필드 {@code u}(사용자 id), {@code f}(패밀리 id), {@code s}(상태 {@code A} 사용 가능 /
 *       {@code U} 회전됨). TTL은 토큰 수명(30일)이다.</li>
 *   <li>{@code rtfam:{패밀리 id}} - 한 로그인에서 이어진 토큰 해시의 집합. 재사용 감지와 로그아웃이 패밀리를 통째로
 *       지울 때 쓴다. TTL은 마지막 회전에서 다시 30일이다.</li>
 * </ul>
 * 회전된 토큰은 지우지 않고 {@code U}로 남긴다 - 남겨야 같은 토큰이 다시 왔을 때 "모르는 토큰"(만료)과 "이미 쓴
 * 토큰"(복사본이 있다)을 가를 수 있다. {@code U} 표시는 원래 TTL이 끝나면 저절로 사라진다.
 *
 * <h4>원자성</h4>
 * 판정과 쓰기는 Lua 스크립트 하나씩이다. 같은 토큰으로 두 refresh가 동시에 와도 Redis가 스크립트를 하나씩 실행하므로
 * 하나만 {@code A}를 보고 회전하고, 뒤의 것은 {@code U}를 보고 재사용으로 판정된다 - 클라이언트가 refresh를
 * 직렬화해야 하는 이유다 (§3.12, KAN-224). 스크립트가 선언하지 않은 키({@code rtfam:*}, 새 {@code rt:*})를 안에서
 * 만드는 것은 단일 노드라서 성립한다 - 클러스터 모드로 바꾸면 해시 태그로 키를 한 슬롯에 모아야 한다.
 *
 * <h4>장애</h4>
 * Redis에 닿지 못하거나 시간이 넘으면 503 {@code AUTH_STORE_UNAVAILABLE}이다. 익명 응시는 이 클래스를 거치지
 * 않으므로 영향이 없다 (NFR-AV-02).
 */
@Component
public class RefreshTokens {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokens.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /** 토큰 난수 길이 - 256bit (세션 토큰과 같다). */
    private static final int TOKEN_BYTES = 32;

    static final String PREFIX = "rt_";

    /** 원문 형식 - {@code rt_} + base64url 43자. 형식부터 틀리면 Redis에 묻지 않는다. */
    private static final Pattern FORMAT = Pattern.compile("rt_[A-Za-z0-9_-]{43}");

    static final String TOKEN_KEY = "rt:";
    static final String FAMILY_KEY = "rtfam:";

    /**
     * 새 패밀리의 첫 토큰. KEYS[1] = rt:{해시}, KEYS[2] = rtfam:{패밀리}. ARGV = 사용자 id, 패밀리 id, TTL(ms), 해시.
     */
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            redis.call('HSET', KEYS[1], 'u', ARGV[1], 'f', ARGV[2], 's', 'A')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            redis.call('SADD', KEYS[2], ARGV[4])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    /**
     * 회전. KEYS[1] = rt:{낸 토큰의 해시}. ARGV = 새 토큰의 해시, TTL(ms), 낸 토큰의 해시.
     * 결과 = {상태, 사용자 id} - 상태는 OK, REUSED, INVALID 중 하나다.
     * <p>
     * 토큰이 패밀리 집합에 없으면 무효다 - 패밀리가 폐기됐는데 토큰 키만 남은 상태(부분 삭제, 메모리 축출)에서
     * 그 토큰으로 패밀리를 되살리지 않는다 (Codex 리뷰 P1). 운영 Redis는 축출 자체를 끈다 (noeviction, infra data 모듈).
     * <p>
     * 회전할 때 TTL이 끝나 키가 사라진 멤버를 집합에서 걷어 낸다 (Codex 2차 리뷰 P2). 계속 쓰는 로그인은 패밀리 TTL이
     * 회전마다 늘어나므로, 걷어 내지 않으면 집합이 30일보다 오래된 해시까지 끝없이 쌓인다. 걷어 낸 뒤의 크기는 30일 안에
     * 회전한 횟수(Access 30분 기준 최대 1,440개)가 상한이다.
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE = RedisScript.of("""
            local v = redis.call('HMGET', KEYS[1], 'u', 'f', 's')
            if not v[1] then
              return {'INVALID'}
            end
            local family = 'rtfam:' .. v[2]
            if redis.call('SISMEMBER', family, ARGV[3]) == 0 then
              redis.call('DEL', KEYS[1])
              return {'INVALID'}
            end
            if v[3] ~= 'A' then
              for _, member in ipairs(redis.call('SMEMBERS', family)) do
                redis.call('DEL', 'rt:' .. member)
              end
              redis.call('DEL', family)
              return {'REUSED', v[1]}
            end
            redis.call('HSET', KEYS[1], 's', 'U')
            for _, member in ipairs(redis.call('SMEMBERS', family)) do
              if redis.call('EXISTS', 'rt:' .. member) == 0 then
                redis.call('SREM', family, member)
              end
            end
            local fresh = 'rt:' .. ARGV[1]
            redis.call('HSET', fresh, 'u', v[1], 'f', v[2], 's', 'A')
            redis.call('PEXPIRE', fresh, ARGV[2])
            redis.call('SADD', family, ARGV[1])
            redis.call('PEXPIRE', family, ARGV[2])
            return {'OK', v[1]}
            """, List.class);

    /**
     * 패밀리 폐기. KEYS[1] = rt:{해시}. ARGV[1] = 주인이어야 하는 사용자 id(빈 문자열이면 주인을 묻지 않는다).
     * 결과 1이면 폐기했고 0이면 모르는 토큰이거나 다른 사용자의 것이다.
     */
    private static final RedisScript<Long> REVOKE_FAMILY = RedisScript.of("""
            local v = redis.call('HMGET', KEYS[1], 'u', 'f')
            if not v[1] then
              return 0
            end
            if ARGV[1] ~= '' and v[1] ~= ARGV[1] then
              return 0
            end
            local family = 'rtfam:' .. v[2]
            for _, member in ipairs(redis.call('SMEMBERS', family)) do
              redis.call('DEL', 'rt:' .. member)
            end
            redis.call('DEL', family)
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    RefreshTokens(StringRedisTemplate redis, AccenturyProperties properties) {
        this.redis = redis;
        this.ttl = properties.auth().refreshTokenTtl();
    }

    /** 새 로그인 = 새 패밀리의 첫 토큰. 원문을 돌려준다 - 원문은 이 반환값 말고 어디에도 없다. */
    public String issue(UUID userId) {
        String token = newToken();
        String hash = hash(token);
        String family = UUID.randomUUID().toString();
        call(() -> redis.execute(ISSUE, List.of(TOKEN_KEY + hash, FAMILY_KEY + family),
                userId.toString(), family, String.valueOf(ttl.toMillis()), hash));
        return token;
    }

    /**
     * 회전 결과.
     *
     * @param userId 토큰의 주인 (REUSED와 OK일 때)
     * @param token  새 Refresh 원문 (OK일 때)
     */
    public record Rotation(UUID userId, String token) {
    }

    /**
     * 낸 토큰을 {@code U}로 바꾸고 같은 패밀리의 새 토큰을 준다 (§3.12).
     *
     * @throws ApiException 401 {@code AUTH_REFRESH_INVALID} - 형식이 틀리거나, 없거나, 만료됐다<br>
     *                      401 {@code AUTH_REFRESH_REUSED} - 이미 회전된 토큰이다. 패밀리 전체를 이미 지웠다<br>
     *                      503 {@code AUTH_STORE_UNAVAILABLE} - Redis 장애
     */
    public Rotation rotate(String presented) {
        if (!FORMAT.matcher(presented).matches()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        String fresh = newToken();
        String presentedHash = hash(presented);
        List<?> result = call(() -> redis.execute(ROTATE, List.of(TOKEN_KEY + presentedHash),
                hash(fresh), String.valueOf(ttl.toMillis()), presentedHash));
        String status = result == null || result.isEmpty() ? "INVALID" : String.valueOf(result.getFirst());
        switch (status) {
            case "OK" -> {
                return new Rotation(UUID.fromString(String.valueOf(result.get(1))), fresh);
            }
            case "REUSED" -> {
                // 사용자 id만 남긴다 - 토큰 해시도 남기지 않는다. 탈취 의심이라 운영자가 볼 수 있게 warn이다.
                log.warn("Refresh 재사용 감지 - 토큰 패밀리 전체 폐기 userId={}", result.get(1));
                throw new ApiException(ErrorCode.AUTH_REFRESH_REUSED);
            }
            default -> throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
    }

    /**
     * 토큰이 속한 패밀리를 통째로 폐기한다 - 로그아웃(§3.13)과, 회전 직후 계정이 사라진 것을 알았을 때 쓴다.
     *
     * @param ownerId 이 사용자의 토큰일 때만 지운다. null이면 주인을 묻지 않는다.
     * @return 폐기했는가 - 모르는 토큰이나 남의 토큰이면 false
     */
    public boolean revokeFamily(String presented, @Nullable UUID ownerId) {
        if (!FORMAT.matcher(presented).matches()) {
            return false;
        }
        Long revoked = call(() -> redis.execute(REVOKE_FAMILY, List.of(TOKEN_KEY + hash(presented)),
                ownerId != null ? ownerId.toString() : ""));
        return revoked != null && revoked == 1L;
    }

    private static <T> T call(Supplier<T> redisCall) {
        try {
            return redisCall.get();
        } catch (DataAccessException e) {
            // 연결 실패, 타임아웃, 스크립트 오류가 전부 여기로 온다 (Spring Data Redis의 예외 변환).
            log.error("Refresh 저장소(Redis) 호출 실패 - {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + BASE64_URL.encodeToString(bytes);
    }

    /** 저장과 조회용 SHA-256 해시 (hex 64자). */
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM", e);
        }
    }
}
