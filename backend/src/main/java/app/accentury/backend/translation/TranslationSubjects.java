package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * 번역 기록의 계정별 대체 ID 대응표 (KAN-266, V12 {@code translation_subject}, 명세서 §3.18).
 * <p>
 * 번역 기록 객체에는 계정 ID 대신 대체 ID를 적는다. 대체 ID는 계정이 처음 기록될 때 무작위로 만들고, 탈퇴
 * ({@code WithdrawalService})가 계정 파기와 같은 트랜잭션에서 행을 지운다 - S3 객체는 그대로 남고 누구의 것인지 알 수 없게
 * 된다. 버킷이 없는 환경에서도 빈으로 뜬다 (탈퇴가 부른다). 행을 만드는 쪽은 기록 저장소뿐이다.
 */
@Component
public class TranslationSubjects {

    private final JdbcClient jdbc;

    TranslationSubjects(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 이 계정의 대체 ID - 없으면 만든다. 탈퇴한 계정이면 만들지 않고 null이다.
     * <p>
     * 계정 행의 공유 잠금 아래에서만 넣는다({@code for share}). 탈퇴는 같은 행을 배타 잠금으로 잡고 대응표 행을 지우므로,
     * 둘이 겹치면 한쪽이 기다린다 - 넣기가 먼저면 탈퇴가 그 행을 지우고, 탈퇴가 먼저면 {@code deleted_at}이 찍혀 넣지
     * 않는다. 잠금이 없으면 탈퇴 직후의 비동기 기록이 탈퇴한 계정에 새 대체 ID를 다시 만들 수 있다.
     */
    @Nullable UUID resolve(UUID userId, Instant now) {
        jdbc.sql("""
                        insert into translation_subject (user_id, subject_id, created_at)
                        select :userId, :subjectId, :createdAt
                        where exists (select 1 from app_user where id = :userId and deleted_at is null for share)
                        on conflict (user_id) do nothing
                        """)
                .param("userId", userId)
                .param("subjectId", UUID.randomUUID())
                .param("createdAt", Timestamp.from(now))
                .update();
        return jdbc.sql("select subject_id from translation_subject where user_id = :userId")
                .param("userId", userId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    /** 탈퇴 - 이 계정의 대응표 행을 지운다. 탈퇴 트랜잭션 안에서 부른다. 지운 행 수(0 또는 1). */
    public int purge(UUID userId) {
        return jdbc.sql("delete from translation_subject where user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
