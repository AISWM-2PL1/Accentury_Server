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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 게스트 계정 발급의 IP당 하루 상한 (KAN-329, 명세서 §2.5, §3.21).
 * <p>
 * 게스트 계정은 만들 때마다 번역 하루 한도(성공 5건, 호출 20번)가 새로 생긴다. 발급을 묶지 않으면 한 사람이 게스트를
 * 계속 만들어 프로젝트 전체가 나눠 쓰는 번역 사업자의 하루 몫을 비울 수 있다. 분당 요청 제한({@code RateLimits})은
 * 메모리에서 세어 backend 태스크 수만큼 풀리고 하루 총량을 막지 못하므로, 번역 한도처럼 Redis에서 센다.
 *
 * <h4>무엇을 한 통으로 세는가</h4>
 * IPv4는 주소 하나, <b>IPv6는 앞 64비트(/64)</b>다 (PR #69 리뷰). IPv6는 가입자 한 명에게 /64 대역이 통째로 가서
 * 주소만 바꾸면 통이 매번 새로 생긴다 - 주소 단위로 세면 엣지가 IPv6를 받는 한(CloudFront) 상한이 걸리지 않는다.
 *
 * <h4>키에 IP를 두지 않는다</h4>
 * 키는 {@code gq:{HMAC-SHA256(통)}:{yyyyMMdd}}(KST 날짜)이고 다음 날 KST 01시에 만료된다. 솔트 없는 해시는 쓰지 않는다
 * (PR #69 리뷰) - IPv4는 2^32개뿐이라 전부 대입하면 몇 초 만에 키에서 IP가 되살아난다. HMAC의 키는 Access 토큰 서명
 * 키에서 끌어낸다 ({@link AccessTokens#deriveKey}). 서명 키를 바꾸면 그날의 카운터가 새로 시작하는데, 하루짜리 값이라
 * 그대로 둔다.
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
    private static final String HMAC = "HmacSHA256";

    /** 키 끌어내기의 용도 표지 - 바꾸면 진행 중인 날의 카운터가 새로 시작한다. */
    private static final String KEY_PURPOSE = "accentury:guest-quota:v1";

    /**
     * 한 칸 잡기 - 상한을 넘으면 되돌리고 0이다. 세기와 만료 설정이 스크립트 하나라, 사이에서 죽은 요청이 만료 없는 키를
     * 남기지 않고 동시 요청이 함께 통과하지도 못한다. 넘은 요청이 카운터를 올린 채 두면 {@link #RELEASE}와 셈이 어긋난다.
     */
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
    private final byte[] hmacKey;

    @Autowired
    GuestQuota(StringRedisTemplate redis, AccessTokens accessTokens) {
        this(redis, Clock.systemUTC(), accessTokens.deriveKey(KEY_PURPOSE));
    }

    GuestQuota(StringRedisTemplate redis, Clock clock, byte[] hmacKey) {
        this.redis = redis;
        this.clock = clock;
        this.hmacKey = hmacKey.clone();
    }

    /**
     * 이 IP의 오늘 발급 한 칸을 잡는다.
     *
     * @return 잡은 칸의 키 - 발급이 실패하면 {@link #release}에 그대로 넘긴다. 날짜가 든 키를 돌려주는 것은 자정을
     *         넘긴 실패가 다음 날의 칸을 깎지 않게 하려는 것이다
     * @throws ApiException 429 {@code AUTH_GUEST_LIMIT_EXCEEDED} - 오늘 상한을 넘었다<br>
     *                      503 {@code AUTH_STORE_UNAVAILABLE} - Redis 장애. 세지 못하면 발급하지 않는다
     */
    String acquire(String clientIp) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        String key = key(clientIp, today);
        long expireAt = today.plusDays(1).atTime(EXPIRE_AT).atZone(KST).toInstant().toEpochMilli();
        Long used;
        try {
            used = redis.execute(ACQUIRE, List.of(key),
                    String.valueOf(AccenturyProperties.Auth.GUEST_DAILY_LIMIT_PER_IP), String.valueOf(expireAt));
        } catch (DataAccessException e) {
            // 연결 실패, 타임아웃, 스크립트 오류가 전부 여기로 온다 (Spring Data Redis의 예외 변환).
            log.error("게스트 발급 상한 저장소(Redis) 호출 실패 - {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
        if (used == null || used <= 0) {
            throw new ApiException(ErrorCode.AUTH_GUEST_LIMIT_EXCEEDED);
        }
        return key;
    }

    /**
     * 잡은 칸을 돌려준다 - 발급이 계정을 만들지 못했거나 토큰을 주지 못했을 때다 (PR #69 리뷰). 돌려주지 않으면 서버
     * 장애 동안의 재시도가 그 IP의 하루 몫을 다 써서, 장애가 끝난 뒤에도 그날은 게스트로 들어오지 못한다. 실패해도
     * 던지지 않는다 - 원래의 실패가 응답으로 나가야 한다.
     */
    void release(String key) {
        try {
            redis.execute(RELEASE, List.of(key));
        } catch (DataAccessException e) {
            log.warn("게스트 발급 상한 칸 반환 실패 - 그 칸은 자정까지 잃는다 ({})", e.getClass().getSimpleName());
        }
    }

    String key(String clientIp, LocalDate day) {
        return KEY + hmac(bucket(clientIp)) + ":" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * 한 통으로 셀 단위 - IPv4는 주소 그대로, IPv6는 앞 64비트다. IPv4를 감싼 IPv6 주소({@code ::ffff:a.b.c.d})는
     * {@link InetAddress#ofLiteral}이 IPv4로 풀어 준다. IP로 읽히지 않는 값은 그대로 한 통이다 - 호출 쪽
     * ({@code ClientIps})이 IP 문자열만 넘기므로 도달하지 않는 방어선이고, 읽지 못했다고 상한을 풀지 않는다.
     */
    static String bucket(String clientIp) {
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(clientIp);
        } catch (IllegalArgumentException e) {
            return clientIp;
        }
        if (address instanceof Inet6Address v6) {
            return HexFormat.of().formatHex(v6.getAddress(), 0, 8) + "/64";
        }
        return address.getHostAddress();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(hmacKey, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 쓸 수 없는 JVM", e);
        }
    }
}
