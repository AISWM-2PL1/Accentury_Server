package app.accentury.backend.translation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 번역 결과 신고의 저장소 (KAN-343, V19 {@code translation_report}, 명세서 §3.22).
 * <p>
 * 신고에는 계정 id가 붙는다. 탈퇴와 게스트 계정 파기({@code WithdrawalService})가 계정 파기와 같은 트랜잭션에서 그
 * 계정의 신고에서 계정 id만 비운다 - 신고 내용은 보존 기간까지 남고 누구의 것인지 알 수 없게 된다 (2026-11-10 결정).
 */
@Component
public class TranslationReports {

    private final JdbcClient jdbc;

    TranslationReports(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 저장된 신고 한 건 - 슬랙 알림이 읽는다.
     *
     * @param userId 신고한 계정 - 그사이 탈퇴했으면 null이다
     */
    record Report(UUID id, @org.jspecify.annotations.Nullable UUID userId, String direction, String sourceText,
                  String resultText, Instant createdAt) {
    }

    /**
     * 신고를 넣는다. 살아 있는 계정일 때만 넣는다.
     * <p>
     * 계정 행의 공유 잠금 아래에서 넣는다 ({@code for share}, {@link TranslationSubjects#resolve}와 같은 규율). 탈퇴는
     * 같은 행을 배타 잠금으로 잡고 이 계정의 신고에서 계정 id를 비우므로, 잠금이 없으면 탈퇴 직후에 들어온 신고가
     * 탈퇴한 계정의 id를 다시 달고 남는다.
     *
     * @return 넣었는가 - 그사이 탈퇴한 계정이면 false
     */
    boolean save(UUID id, UUID userId, TranslationDirection direction, String sourceText, String resultText,
                 Instant now) {
        return jdbc.sql("""
                        insert into translation_report (id, user_id, direction, source_text, result_text, created_at)
                        select :id, :userId, :direction, :sourceText, :resultText, :createdAt
                        where exists (select 1 from app_user where id = :userId and deleted_at is null for share)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("direction", direction.apiValue())
                .param("sourceText", sourceText)
                .param("resultText", resultText)
                .param("createdAt", Timestamp.from(now))
                .update() == 1;
    }

    Optional<Report> find(UUID id) {
        return jdbc.sql("""
                        select id, user_id, direction, source_text, result_text, created_at
                        from translation_report where id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new Report(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("direction"),
                        rs.getString("source_text"),
                        rs.getString("result_text"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    /** 탈퇴와 게스트 계정 파기 - 이 계정의 신고에서 계정 id를 비운다. 계정 파기 트랜잭션 안에서 부른다. 바꾼 행 수. */
    public int unlink(UUID userId) {
        return jdbc.sql("update translation_report set user_id = null where user_id = :userId")
                .param("userId", userId)
                .update();
    }

    /** 보존 기간 정리 - {@code cutoff}보다 먼저 들어온 신고를 지운다. 경계는 미만이다. 지운 행 수. */
    int deleteCreatedBefore(Instant cutoff) {
        return jdbc.sql("delete from translation_report where created_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
