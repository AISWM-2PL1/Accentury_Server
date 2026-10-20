package app.accentury.backend.learning;

import app.accentury.backend.analysis.AnalysisJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * 억양 학습 채점 시도 하나 = 카드 1건의 녹음 업로드이자 AI 분석 1건 (KAN-267, 명세서 §3.19, V13).
 * <p>
 * 상태는 레벨테스트 분석 작업과 같은 값이다({@link AnalysisJobStatus}) - 같은 전달 큐와 같은 종결 규칙을 지나기
 * 때문이다. 종결 뒤의 값(점수, 피드백, 오류 코드)은 조건부 UPDATE({@link IntonationAttemptRepository})로만 채운다 -
 * 늦게 온 결과가 이미 종결된 시도를 덮지 않게 엔티티 메서드로는 바꾸지 않는다.
 */
@Entity
@Table(name = "intonation_learning_attempt")
public class IntonationAttempt implements Persistable<String> {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_version", nullable = false, length = 40)
    private String contentVersion;

    @Column(name = "card_id", nullable = false, length = 40)
    private String cardId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisJobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private @Nullable Instant startedAt;

    @Column(name = "finished_at")
    private @Nullable Instant finishedAt;

    /** AI 원점수 (0~100) - 응답에는 싣지 않는다. */
    @Column(name = "raw_score")
    private @Nullable Integer rawScore;

    /** 사용자에게 보이는 변환 점수 (§3.19). */
    @Column(name = "score")
    private @Nullable Integer score;

    /** 올리고 내릴 음절 JSON 배열 ({@link PitchFeedback}). */
    @Column(name = "pitch_feedback")
    private @Nullable String pitchFeedback;

    @Column(name = "quality_code", length = 40)
    private @Nullable String qualityCode;

    @Column(name = "error_code", length = 40)
    private @Nullable String errorCode;

    @Column(name = "model_version", length = 60)
    private @Nullable String modelVersion;

    @Column(name = "score_version", length = 40)
    private @Nullable String scoreVersion;

    @Transient
    private boolean isNew = false;

    protected IntonationAttempt() {
        // JPA 전용
    }

    public IntonationAttempt(String id, UUID userId, String contentVersion, String cardId, String idempotencyKey,
                             Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.contentVersion = contentVersion;
        this.cardId = cardId;
        this.idempotencyKey = idempotencyKey;
        this.status = AnalysisJobStatus.PROCESSING;
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @PostPersist
    void markPersisted() {
        this.isNew = false;
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

    public String cardId() {
        return cardId;
    }

    public AnalysisJobStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Integer rawScore() {
        return rawScore;
    }

    public @Nullable Integer score() {
        return score;
    }

    public @Nullable String pitchFeedback() {
        return pitchFeedback;
    }

    public @Nullable String errorCode() {
        return errorCode;
    }

    public @Nullable String modelVersion() {
        return modelVersion;
    }

    public @Nullable String scoreVersion() {
        return scoreVersion;
    }
}
