package app.accentury.backend.learning;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WordSetAttemptRepository extends JpaRepository<WordSetAttempt, String> {

    /**
     * 답안 저장과 완료 전이가 잡는 시도 행 잠금 - 완료 가드와 저장을 한 트랜잭션으로 묶는다
     * ({@code TestSessionRepository.lockById}와 같은 규칙). 호출부에 트랜잭션 필요.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WordSetAttempt a where a.id = :id")
    Optional<WordSetAttempt> lockById(@Param("id") String id);

    /** 계정의 시도 전부 - 탈퇴 검증과 진도(KAN-268)가 쓴다. */
    List<WordSetAttempt> findByUserId(UUID userId);

    /**
     * 진도 (KAN-268, §3.16) - 이 계정이 이 발행본에서 완료한 시도, 최근 완료가 앞이다. 세트마다 첫 행이 최근 정답률이다.
     */
    @Query("""
            select a from WordSetAttempt a
             where a.userId = :userId and a.contentVersion = :contentVersion and a.completedAt is not null
             order by a.completedAt desc
            """)
    List<WordSetAttempt> findCompletedNewestFirst(@Param("userId") UUID userId,
                                                  @Param("contentVersion") String contentVersion);

    /**
     * 출석 달력 (KAN-314, §3.20) - 이 계정이 [from, to) 사이에 완료한 시도. 한 달 범위로 부른다. 순서는 호출부가 하루
     * 안에서 다시 정하므로 여기서 정렬하지 않는다.
     */
    @Query("""
            select a from WordSetAttempt a
             where a.userId = :userId and a.completedAt >= :from and a.completedAt < :to
            """)
    List<WordSetAttempt> findCompletedBetween(@Param("userId") UUID userId, @Param("from") Instant from,
                                              @Param("to") Instant to);

    /**
     * 탈퇴 때 계정의 시도를 전부 지운다 (§3.14). 답안은 FK {@code on delete cascade}로 함께 지워진다.
     * 호출부에 트랜잭션 필요.
     */
    @Modifying
    @Query("delete from WordSetAttempt a where a.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
