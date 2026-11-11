package app.accentury.backend.auth;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link AppUser} 저장소 (KAN-223). 탈퇴 표시({@code deletedAt})가 있는 행은 조회에서 뺀다. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** 계정 식별 키 (provider, IdP 사용자 id)로 찾는다 - 탈퇴 표시가 있어도 찾는다(유일 제약이 걸린 키라서). */
    Optional<AppUser> findByProviderAndProviderUserId(Provider provider, String providerUserId);

    /**
     * 로그인용 - 계정 식별 키로 찾되 행 잠금과 함께 읽는다. 재로그인의 빈 열 채우기와 프로필 저장이 겹칠 때, 잠금 없이 읽은
     * 옛 값이 뒤늦게 커밋되며 새 프로필을 덮는 것을 막는다 (Codex 리뷰 P2) - {@link #lockActive}와 같은 규율이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.provider = :provider and u.providerUserId = :providerUserId")
    Optional<AppUser> lockByProviderAndProviderUserId(Provider provider, String providerUserId);

    /** 살아 있는 계정 - Access 토큰의 {@code sub}를 사용자로 바꾸는 자리다. */
    @Query("select u from AppUser u where u.id = :id and u.deletedAt is null")
    Optional<AppUser> findActive(UUID id);

    /**
     * 살아 있는 계정을 행 잠금과 함께 읽는다 - 프로필 갱신과 재로그인의 빈 열 채우기가 같은 행을 동시에
     * 고칠 때 뒤의 쓰기가 앞의 값을 되돌리지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id and u.deletedAt is null")
    Optional<AppUser> lockActive(UUID id);

    /**
     * 살아 있는 게스트 계정의 id를 id 순서로 한 쪽 읽는다 - 게스트 계정 정리({@link GuestAccountRetention}, KAN-329)가
     * 전부를 훑을 때 쓴다. {@code after}보다 큰 id부터라 앞 쪽을 다시 읽지 않는다 (키셋).
     * <p>
     * V18의 부분 색인 {@code ix_app_user_live_guest}(살아 있는 게스트의 id)가 조건과 정렬을 함께 받는다 (PR #69 리뷰).
     * 파기된 게스트 행은 지우지 않고 남기므로, 이 색인이 없으면 쌓인 행을 쪽마다 다시 훑게 된다. provider를 인자가
     * 아니라 리터럴로 적은 것도 그래서다 - 인자로 두면 준비된 문장의 일반 계획이 부분 색인의 조건을 증명하지 못해
     * 색인을 쓰지 않는다.
     */
    @Query("select u.id from AppUser u where u.provider = app.accentury.backend.auth.Provider.GUEST"
            + " and u.deletedAt is null and u.createdAt < :createdBefore and u.id > :after order by u.id")
    List<UUID> findLiveGuestIdsCreatedBefore(Instant createdBefore, UUID after, Pageable pageable);
}
