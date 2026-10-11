package app.accentury.backend.learning;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 계정의 레벨테스트 최근 등급 기록 (KAN-268, V14 {@code level_test_record}, 명세서 §3.20).
 * <p>
 * 세션과 결과는 완주 24시간 뒤 행째 지워지므로(§5.5) 홈의 테스트 전후 판정과 추천은 이 표를 읽는다. 계정당 1행이고
 * 완료 시각이 더 늦은 결과만 덮어쓴다. 등급만 남기고 점수는 남기지 않는다.
 * <p>
 * <b>쓰기는 결과 확정 트랜잭션 밖에서 한다</b> ({@code CompletionService}가 커밋 뒤에 부른다). 완료는 세션 행을 잠근 채
 * 결과를 쓰고, 탈퇴({@code WithdrawalService})는 계정 행을 잠근 채 세션의 계정 귀속을 끊는다 - 같은 트랜잭션에서 이 행을
 * 넣으면 FK 검사가 계정 행을 기다리는 동안 탈퇴는 세션 행을 기다려 교착이 된다. 그래서 {@link TranslationSubjects}와 같이
 * 계정 행의 공유 잠금 아래 한 문장으로 넣는다 - 탈퇴가 먼저면 {@code deleted_at}이 찍혀 넣지 않고, 넣기가 먼저면 탈퇴가
 * {@link LearningRecords#purge}로 지운다.
 *
 * @see app.accentury.backend.translation.TranslationSubjects
 */
@Component
public class LevelTestRecords {

    /** 저장된 최근 등급 1행. */
    public record Entry(String testVersion, String scoreVersion, String tierCode, String tierName, int tierRank,
                        int tierCount, Instant completedAt) {
    }

    private final JdbcClient jdbc;

    LevelTestRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 확정된 결과 하나를 계정의 최근 등급으로 남긴다. 자기 트랜잭션(자동 커밋 한 문장)이다.
     *
     * @return 행을 넣었거나 덮었으면 true. 탈퇴한 계정이거나 이미 더 늦은 기록이 있으면 false
     */
    public boolean record(UUID userId, Entry entry) {
        return jdbc.sql("""
                        insert into level_test_record
                            (user_id, test_version, score_version, tier_code, tier_name, tier_rank, tier_count,
                             completed_at)
                        select :userId, :testVersion, :scoreVersion, :tierCode, :tierName, :tierRank, :tierCount,
                               :completedAt
                        where exists (select 1 from app_user where id = :userId and deleted_at is null for share)
                        on conflict (user_id) do update
                            set test_version = excluded.test_version,
                                score_version = excluded.score_version,
                                tier_code = excluded.tier_code,
                                tier_name = excluded.tier_name,
                                tier_rank = excluded.tier_rank,
                                tier_count = excluded.tier_count,
                                completed_at = excluded.completed_at
                            where level_test_record.completed_at < excluded.completed_at
                        """)
                .param("userId", userId)
                .param("testVersion", entry.testVersion())
                .param("scoreVersion", entry.scoreVersion())
                .param("tierCode", entry.tierCode())
                .param("tierName", entry.tierName())
                .param("tierRank", entry.tierRank())
                .param("tierCount", entry.tierCount())
                .param("completedAt", Timestamp.from(entry.completedAt()))
                .update() > 0;
    }

    /** 계정의 최근 등급 - 완주한 적이 없으면 빈 값이다. */
    public Optional<Entry> find(UUID userId) {
        return jdbc.sql("""
                        select test_version, score_version, tier_code, tier_name, tier_rank, tier_count, completed_at
                          from level_test_record where user_id = :userId
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new Entry(rs.getString("test_version"), rs.getString("score_version"),
                        rs.getString("tier_code"), rs.getString("tier_name"), rs.getInt("tier_rank"),
                        rs.getInt("tier_count"), rs.getTimestamp("completed_at").toInstant()))
                .optional();
    }

    /** 탈퇴 - 이 계정의 행을 지운다. 탈퇴 트랜잭션 안에서 부른다. 지운 행 수(0 또는 1). */
    int purge(UUID userId) {
        return jdbc.sql("delete from level_test_record where user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
