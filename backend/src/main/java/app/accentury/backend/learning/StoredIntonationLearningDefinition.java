package app.accentury.backend.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 발행된 억양 학습 발행본 한 건의 저장 행 (KAN-264, 명세서 §3.17).
 * <p>
 * {@link StoredWordLearningDefinition}과 같은 규칙이다 - 발행 후 불변이라 변경 메서드가 없고, 발행 경로는 마이그레이션의
 * INSERT뿐이다. 본문은 {@link IntonationLearningDefinition}과 1:1로 파싱된다. {@code dialect}는 본문 값의 사본이고
 * 일치는 기동 시 {@link IntonationLearningRegistry}가 강제한다.
 */
@Entity
@Table(name = "intonation_learning_definition")
public class StoredIntonationLearningDefinition {

    @Id
    @Column(name = "content_version", length = 40)
    private String contentVersion;

    @Column(nullable = false, length = 20)
    private String dialect;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 발행 시각 - 가장 늦은 행이 목록과 상세가 쓰는 발행본이다 (§3.17, 활성 포인터 없음). */
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected StoredIntonationLearningDefinition() {
        // JPA 전용
    }

    public String contentVersion() {
        return contentVersion;
    }

    public String dialect() {
        return dialect;
    }

    public String body() {
        return body;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}
