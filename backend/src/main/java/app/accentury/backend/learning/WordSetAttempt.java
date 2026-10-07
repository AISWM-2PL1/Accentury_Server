package app.accentury.backend.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * 세트 학습 시도 = 계정이 세트 하나를 한 번 푸는 단위 (KAN-265, 명세서 §3.16).
 * <p>
 * 시작 시점의 {@code contentVersion}에 고정된다 - 발행본이 바뀌어도 진행 중인 시도는 자기 버전의 문항을 본다.
 * 완료되면 {@code completedAt}과 {@code correctCount}가 찍히고 그 뒤의 제출은 409다. 같은 세트를 다시 풀면 새 행이다.
 * <p>
 * {@link Persistable}인 이유는 {@code TestSession}과 같다 - 식별자를 직접 정하므로 {@code save()}가 merge로 가면
 * 시도마다 빗나가는 SELECT 한 번을 낸다.
 */
@Entity
@Table(name = "word_set_attempt")
public class WordSetAttempt implements Persistable<String> {

    /** 형식: {@code wa_} + UUID */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_version", nullable = false, length = 40)
    private String contentVersion;

    @Column(name = "set_id", nullable = false, length = 40)
    private String setId;

    /** 세트 문항 수 - 완료 판정의 분모. 발행본이 불변이라 사본을 둬도 어긋나지 않는다. */
    @Column(name = "item_count", nullable = false)
    private int itemCount;

    /** 완료 시점의 정답 수 - 완료 전에는 null. */
    @Column(name = "correct_count")
    private @Nullable Integer correctCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Transient
    private boolean isNew = false;

    protected WordSetAttempt() {
        // JPA 전용
    }

    public WordSetAttempt(String id, UUID userId, String contentVersion, String setId, int itemCount, Instant startedAt) {
        this.id = id;
        this.userId = userId;
        this.contentVersion = contentVersion;
        this.setId = setId;
        this.itemCount = itemCount;
        this.startedAt = startedAt;
        this.isNew = true;
    }

    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }

    /** 완료 전이 - 시도 행 잠금 아래에서만 부른다 ({@link WordLearningService#complete}). */
    void complete(Instant at, int correctCount) {
        this.completedAt = at;
        this.correctCount = correctCount;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
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

    public int itemCount() {
        return itemCount;
    }

    public @Nullable Integer correctCount() {
        return correctCount;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }
}
