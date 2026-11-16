package app.accentury.backend.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 문항 답안 = 시도 안 문항당 1행 (KAN-265, 명세서 §3.16).
 * <p>
 * {@code VocabAnswer}와 같은 규칙이다 - (attempt_id, item_id) 유니크가 "문항당 답안은 하나"를 DB에서 강제하고,
 * 제출은 시도 행 잠금으로 직렬화되므로 이 제약은 마지막 안전망이다. 정오는 제출 시점에 발행본 정답표와
 * 대조해 확정한다 - 발행본이 불변이라 완료 시점에 다시 대조해도 같다.
 * <p>
 * 시간 초과 답안(KAN-360, V22)은 고른 보기가 없어 {@code choiceId}가 null이고 늘 오답이다. 시간 초과 여부를 따로
 * 저장하지 않고 {@link #timedOut()}이 보기의 null로 판별한다. 보기가 없는데 정답인 행은 생성자와 DB의 check 제약
 * ({@code ck_word_attempt_answer_timeout_wrong})이 함께 막는다.
 */
@Entity
@Table(name = "word_attempt_answer",
        uniqueConstraints = @UniqueConstraint(name = "ux_word_attempt_answer_attempt_item",
                columnNames = {"attempt_id", "item_id"}))
public class WordAttemptAnswer {

    /** 형식: {@code waa_} + UUID */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "attempt_id", nullable = false, length = 40)
    private String attemptId;

    @Column(name = "item_id", nullable = false, length = 40)
    private String itemId;

    /** 고른 보기 - 시간 초과 답안이면 null이다. */
    @Column(name = "choice_id", length = 40)
    private @Nullable String choiceId;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    /** 클라이언트가 보낸 Idempotency-Key - 재전송(같은 키)과 재제출(새 키)을 가른다 (§5.2). */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordAttemptAnswer() {
        // JPA 전용
    }

    /**
     * @param choiceId 고른 보기 - 시간 초과면 null이고 그때 {@code correct}는 false여야 한다
     */
    public WordAttemptAnswer(String id, String attemptId, String itemId, @Nullable String choiceId, boolean correct,
                             String idempotencyKey, Instant createdAt) {
        if (choiceId == null && correct) {
            throw new IllegalArgumentException("시간 초과 답안은 정답일 수 없다");
        }
        this.id = id;
        this.attemptId = attemptId;
        this.itemId = itemId;
        this.choiceId = choiceId;
        this.correct = correct;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String attemptId() {
        return attemptId;
    }

    public String itemId() {
        return itemId;
    }

    public @Nullable String choiceId() {
        return choiceId;
    }

    /** 제한 시간 안에 보기를 고르지 않은 답안인지. */
    public boolean timedOut() {
        return choiceId == null;
    }

    public boolean correct() {
        return correct;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
