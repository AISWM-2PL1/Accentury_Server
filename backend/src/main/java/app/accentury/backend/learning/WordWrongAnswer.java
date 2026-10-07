package app.accentury.backend.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 계정별 오답 (FR-WD-04, KAN-265, 명세서 §3.16) - 복습 API(M9, 범위 밖)가 읽을 자리.
 * <p>
 * (user_id, content_version, item_id)당 한 행이고 틀릴 때마다 {@code wrongCount}와 {@code lastWrongAt}이 갱신된다.
 * 갱신은 {@link WordWrongAnswerRepository#recordWrong}의 upsert 한 문장이라 이 엔티티에 변경 메서드가 없다 -
 * 같은 계정의 두 시도가 같은 문항을 동시에 틀려도 유니크 충돌 없이 합산된다. 시도와 무관하게 계정에 매달리고
 * 탈퇴 때만 지워진다. 나중에 맞혀도 지우지 않는다.
 */
@Entity
@Table(name = "word_wrong_answer",
        uniqueConstraints = @UniqueConstraint(name = "ux_word_wrong_answer_user_item",
                columnNames = {"user_id", "content_version", "item_id"}))
public class WordWrongAnswer {

    /** 형식: {@code wwa_} + UUID */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_version", nullable = false, length = 40)
    private String contentVersion;

    /** 문항이 속한 세트 - 복습 화면이 세트로 되돌아갈 때 쓰는 사본. */
    @Column(name = "set_id", nullable = false, length = 40)
    private String setId;

    @Column(name = "item_id", nullable = false, length = 40)
    private String itemId;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(name = "first_wrong_at", nullable = false)
    private Instant firstWrongAt;

    @Column(name = "last_wrong_at", nullable = false)
    private Instant lastWrongAt;

    protected WordWrongAnswer() {
        // JPA 전용 - 쓰기는 upsert 쿼리뿐이다.
    }

    public String id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String contentVersion() {
        return contentVersion;
    }

    public String setId() {
        return setId;
    }

    public String itemId() {
        return itemId;
    }

    public int wrongCount() {
        return wrongCount;
    }

    public Instant firstWrongAt() {
        return firstWrongAt;
    }

    public Instant lastWrongAt() {
        return lastWrongAt;
    }
}
