package app.accentury.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
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
}
