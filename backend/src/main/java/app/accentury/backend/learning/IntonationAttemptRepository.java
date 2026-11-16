package app.accentury.backend.learning;

import app.accentury.backend.analysis.AnalysisJobStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 억양 학습 채점 시도 (KAN-267, V13). 전이는 전부 "PROCESSING일 때만"의 조건부 UPDATE다 -
 * {@code AnalysisJobRepository}와 같은 규칙이라, 워커와 타임아웃 스위퍼와 종료 배수가 겹쳐도 먼저 온 쪽만 이긴다.
 */
public interface IntonationAttemptRepository extends JpaRepository<IntonationAttempt, String> {

    /** 멱등 재전송 판별 (§5.2) - 유니크 제약과 같은 키 조합의 단건 조회. */
    Optional<IntonationAttempt> findByUserIdAndContentVersionAndCardIdAndIdempotencyKey(
            UUID userId, String contentVersion, String cardId, String idempotencyKey);

    /**
     * 직전 대비 (§3.19) - 같은 계정, 같은 발행본, 같은 카드에서 이 시도보다 먼저 접수돼 완료된 시도 중 가장 최근 것.
     * (user_id, content_version, card_id, created_at) 인덱스를 탄다.
     */
    Optional<IntonationAttempt> findFirstByUserIdAndContentVersionAndCardIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID userId, String contentVersion, String cardId, AnalysisJobStatus status, Instant before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IntonationAttempt a set a.startedAt = :startedAt
             where a.id = :id and a.status = app.accentury.backend.analysis.AnalysisJobStatus.PROCESSING
            """)
    int markStartedIfProcessing(@Param("id") String id, @Param("startedAt") Instant startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IntonationAttempt a
               set a.status = app.accentury.backend.analysis.AnalysisJobStatus.COMPLETED,
                   a.rawScore = :rawScore, a.score = :score, a.pitchFeedback = :pitchFeedback,
                   a.qualityCode = :qualityCode, a.modelVersion = :modelVersion, a.scoreVersion = :scoreVersion,
                   a.finishedAt = :finishedAt
             where a.id = :id and a.status = app.accentury.backend.analysis.AnalysisJobStatus.PROCESSING
            """)
    int completeIfProcessing(@Param("id") String id,
                             @Param("rawScore") int rawScore,
                             @Param("score") int score,
                             @Param("pitchFeedback") String pitchFeedback,
                             @Param("qualityCode") String qualityCode,
                             @Param("modelVersion") String modelVersion,
                             @Param("scoreVersion") String scoreVersion,
                             @Param("finishedAt") Instant finishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IntonationAttempt a
               set a.status = :failedStatus, a.errorCode = :errorCode, a.finishedAt = :finishedAt
             where a.id in :ids and a.status = app.accentury.backend.analysis.AnalysisJobStatus.PROCESSING
            """)
    int failAllIfProcessing(@Param("ids") Collection<String> ids,
                            @Param("failedStatus") AnalysisJobStatus failedStatus,
                            @Param("errorCode") String errorCode,
                            @Param("finishedAt") Instant finishedAt);

    /**
     * 실행 잔류 정리 - 시작한 지 상한이 지난 시도를 {@code ANALYSIS_TIMEOUT}으로 종결한다 ({@code AnalysisJobTimeout}과
     * 같은 규칙). 스케줄러에서 부르므로 자기 트랜잭션을 연다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IntonationAttempt a
               set a.status = app.accentury.backend.analysis.AnalysisJobStatus.RETRYABLE_FAILED,
                   a.errorCode = :errorCode, a.finishedAt = :finishedAt
             where a.status = app.accentury.backend.analysis.AnalysisJobStatus.PROCESSING
                   and a.startedAt is not null and a.startedAt < :cutoff
            """)
    int failStartedBefore(@Param("cutoff") Instant cutoff,
                          @Param("errorCode") String errorCode,
                          @Param("finishedAt") Instant finishedAt);

    /** 큐 유실 정리 - 시작하지 못한 채 상한이 지난 시도를 {@code ANALYSIS_UNAVAILABLE}로 종결한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IntonationAttempt a
               set a.status = app.accentury.backend.analysis.AnalysisJobStatus.RETRYABLE_FAILED,
                   a.errorCode = :errorCode, a.finishedAt = :finishedAt
             where a.status = app.accentury.backend.analysis.AnalysisJobStatus.PROCESSING
                   and a.startedAt is null and a.createdAt < :cutoff
            """)
    int failUnstartedBefore(@Param("cutoff") Instant cutoff,
                            @Param("errorCode") String errorCode,
                            @Param("finishedAt") Instant finishedAt);

    /** 탈퇴 파기 (§3.14) - 계정의 시도를 전부 지운다. 호출부(탈퇴 트랜잭션)가 트랜잭션을 연다. */
    /**
     * 진도와 세트 점수 (KAN-268, KAN-359, §3.17) - 이 계정이 이 발행본에서 채점을 {@code COMPLETED}로 마친 시도의 카드와
     * 변환 점수. 시도마다 한 행이다 - 카드 완료는 행이 하나라도 있으면이고(점수와 무관, 2026-10-11 결정), 카드별 최고
     * 점수는 호출부가 {@link LearningStars#best}로 접는다 (최고 기록 유지 규칙을 SQL에 두지 않는다). 실패와 처리 중 시도는
     * 나오지 않는다. (user_id, content_version, card_id, created_at) 인덱스를 탄다.
     */
    @Query("""
            select a.cardId as cardId, a.score as score from IntonationAttempt a
             where a.userId = :userId and a.contentVersion = :contentVersion
                   and a.status = app.accentury.backend.analysis.AnalysisJobStatus.COMPLETED
            """)
    List<CardScore> findCompletedCardScores(@Param("userId") UUID userId,
                                            @Param("contentVersion") String contentVersion);

    /** 채점을 마친 시도 하나의 카드와 변환 점수 - 인터페이스 프로젝션이다. */
    interface CardScore {

        String getCardId();

        /** 변환 점수 (0~100) - 채점 완료 전이가 늘 채우지만 컬럼이 null을 허용해 타입은 nullable이다. */
        @Nullable Integer getScore();
    }

    /**
     * 출석 달력 (KAN-314, §3.20) - 이 계정이 [from, to) 사이에 채점을 {@code COMPLETED}로 마친 시도. 한 달 범위로
     * 부른다. 순서는 호출부가 하루 안에서 다시 정하므로 여기서 정렬하지 않는다.
     */
    @Query("""
            select a from IntonationAttempt a
             where a.userId = :userId and a.status = app.accentury.backend.analysis.AnalysisJobStatus.COMPLETED
                   and a.finishedAt >= :from and a.finishedAt < :to
            """)
    List<IntonationAttempt> findCompletedBetween(@Param("userId") UUID userId, @Param("from") Instant from,
                                                 @Param("to") Instant to);

    @Modifying
    @Query("delete from IntonationAttempt a where a.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
