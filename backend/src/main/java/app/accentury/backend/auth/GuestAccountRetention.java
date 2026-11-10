package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 돌아올 수 없게 된 게스트 계정의 파기 (KAN-329, 명세서 §3.21, §5.5).
 * <p>
 * 게스트 계정에 들어가는 유일한 수단은 기기에 있는 Refresh 토큰이다. 30일 동안 쓰지 않아 Refresh가 만료됐거나
 * 로그아웃으로 폐기됐으면 그 계정에는 누구도 다시 들어갈 수 없고, 소셜 계정과 달리 다시 로그인해 되찾을 길도 없다.
 * 앱에는 게스트용 탈퇴 버튼이 없으므로(KAN-330) 이 잡이 유일한 파기 경로다 - 탈퇴와 같은 절차로 지운다
 * ({@link WithdrawalService#purgeGuest}).
 * <p>
 * 판정은 Redis의 사용자 색인({@code rtuser:{id}})이 있는가다. 색인은 발급과 회전 때마다 수명이 다시 30일이 되어 그
 * 사용자의 어느 로그인보다 늦게 끝나고, 마지막 로그인이 폐기되면 바로 사라진다 ({@link RefreshTokens}). 그래서 계정에
 * 마지막 사용 시각을 따로 적지 않는다.
 * <p>
 * Redis가 답하지 못하면 그 회차를 통째로 그만둔다 - "모른다"를 "없다"로 읽으면 장애 한 번에 살아 있는 게스트를 지운다.
 * Redis의 데이터가 사라진 경우(재생성)는 다르다: 그때는 Refresh도 전부 사라져 게스트들이 실제로 돌아올 수 없으므로
 * 지우는 것이 맞다.
 */
@Component
class GuestAccountRetention {

    private static final Logger log = LoggerFactory.getLogger(GuestAccountRetention.class);

    /**
     * 갓 만든 계정을 건드리지 않는 여유. 발급은 계정을 커밋한 뒤에 Refresh를 내므로({@code AuthService.guest}) 그 사이의
     * 계정은 색인이 아직 없다.
     */
    static final Duration GRACE = Duration.ofHours(1);

    private static final int PAGE_SIZE = 200;

    /** 키셋 순회의 시작점 - 난수 UUID(v4)는 이 값이 될 수 없다. */
    private static final UUID FIRST = new UUID(0L, 0L);

    private final AppUserRepository users;
    private final RefreshTokens refreshTokens;
    private final WithdrawalService withdrawalService;

    GuestAccountRetention(AppUserRepository users, RefreshTokens refreshTokens, WithdrawalService withdrawalService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.withdrawalService = withdrawalService;
    }

    /** 다른 정리 잡들과 시작 시점을 어긋나게 둔다. 방침이 약속한 파기 시점은 「30일 뒤 하루 안」이라 6시간 간격이면 넉넉하다. */
    @Scheduled(initialDelay = 65, fixedDelay = 360, timeUnit = TimeUnit.MINUTES)
    void purgeUnreachable() {
        purgeUnreachable(Instant.now());
    }

    /**
     * 살아 있는 게스트 계정을 id 순서로 전부 훑는다. 앞쪽 일부만 보면 오래 쓰는 게스트들이 앞을 채워 뒤의 계정이 영영
     * 검사되지 않는다.
     *
     * @return 파기한 계정 수
     */
    int purgeUnreachable(Instant now) {
        Instant cutoff = now.minus(GRACE);
        UUID after = FIRST;
        int checked = 0;
        int purged = 0;
        while (true) {
            List<UUID> page = users.findIdsByProviderCreatedBefore(Provider.GUEST, cutoff, after,
                    PageRequest.of(0, PAGE_SIZE));
            for (UUID id : page) {
                boolean live;
                try {
                    live = refreshTokens.hasLiveLogin(id);
                } catch (ApiException e) {
                    log.warn("게스트 계정 정리 중단 - Refresh 저장소가 답하지 않는다 checked={} purged={} code={}",
                            checked, purged, e.code());
                    return purged;
                }
                checked++;
                if (!live && withdrawalService.purgeGuest(id)) {
                    purged++;
                }
            }
            if (page.size() < PAGE_SIZE) {
                break;
            }
            after = page.getLast();
        }
        if (purged > 0) {
            log.info("돌아올 수 없는 게스트 계정 {}건 파기 (검사 {}건)", purged, checked);
        }
        return purged;
    }
}
