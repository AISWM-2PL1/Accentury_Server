package app.accentury.backend.training;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * 계정과 저장된 음성의 대응표 (KAN-269, V6 {@code training_voice_owner}).
 * <p>
 * 학습 버킷의 객체 키에는 세션 ID만 있고 세션 행은 만료되면 삭제된다. 이 표가 세션 ID와 계정을 이어 두어,
 * 동의 철회나 탈퇴 때 그 계정의 음성을 찾을 수 있다. 세션마다 한 행이고(문항 수와 무관), 세션 만료와
 * 탈퇴 뒤에도 남긴다. 객체를 지우는 코드는 없다 - 저장된 음성의 처리는 사람이 이 표로 대상을 뽑아서 한다.
 * <p>
 * 버킷이 없는 환경에서도 빈으로 뜬다 - 표는 어디에나 있고, 저장 빈({@code S3TrainingSampleStore})만 부른다.
 */
@Component
public class TrainingVoiceOwners {

    private final JdbcClient jdbc;

    TrainingVoiceOwners(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 이 세션의 음성이 이 계정 것임을 기록한다. 같은 세션의 다음 문항이 다시 불러도 한 행이다 - 여러 태스크가
     * 동시에 불러도 유일 제약 충돌 없이 끝나도록 충돌은 무시한다.
     */
    public void record(String sessionId, UUID userId, Instant now) {
        jdbc.sql("""
                        insert into training_voice_owner (session_id, user_id, created_at)
                        values (:sessionId, :userId, :createdAt)
                        on conflict (session_id) do nothing
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("createdAt", Timestamp.from(now))
                .update();
    }
}
