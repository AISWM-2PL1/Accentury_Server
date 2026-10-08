package app.accentury.backend.learning;

import app.accentury.backend.testdefinition.TestDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 억양 학습 발행본 (KAN-264, 명세서 §3.17) - AI 채점용 대사 식별자까지 든 서버 내부 모델.
 * <p>
 * 발행본은 {@code tools/content/build_intonation_learning.py}가 레벨테스트 음성 풀에서 만들고 마이그레이션의 INSERT로
 * 들어온다. 발행 후 불변이다 - 바뀌면 새 {@code contentVersion}이다. 클라이언트가 받는 모양은
 * {@link IntonationCourseResponse}와 {@link IntonationCourseListResponse}이고 거기에는 {@code scriptKey}가 없다.
 *
 * @param contentVersion 발행본 버전 (예: in-gn-2026.10.1)
 * @param dialect        대상 방언 - MVP는 GYEONGNAM 고정
 * @param courses        코스 (seq 오름차순으로 정렬해 둔다)
 */
public record IntonationLearningDefinition(String contentVersion, String dialect, List<Course> courses) {

    /**
     * 코스 하나 = 같은 레벨의 대사 카드 묶음 (FR-CT-01).
     *
     * @param courseId 코스 식별자 (예: ic01)
     * @param seq      목록 순서 (1부터, 레벨 오름차순)
     * @param level    레벨 1부터 5 - 첫 발행본은 대사의 어절 수로 정했다 (2026-10-08 결정)
     * @param topic    주제 - 첫 발행본은 원천에 주제 정보가 없어 null이다
     * @param title    코스 제목
     * @param cards    대사 카드 (A-2) - seq 오름차순
     */
    public record Course(String courseId, int seq, int level, @Nullable String topic, String title,
                         List<Card> cards) {
    }

    /**
     * 대사 카드 (FR-CT-02).
     *
     * @param cardId             카드 식별자 (예: ic01c01) - 억양 채점(KAN-267)이 받는 값
     * @param seq                코스 안 순서 (1부터)
     * @param standard           표준어 원문 - 확인된 번역이 없으면 null (2026-10-08 결정)
     * @param dialect            사투리 대사
     * @param scriptKey          AI 채점용 대사 식별자 - 응답에 싣지 않는다
     * @param guideF0            가이드 곡선 - 테스트 정의 문항의 곡선과 같은 모양 (§3.2)
     * @param referenceAudioPath 기준 음원의 상대 경로 - 음원이 없으면 null (확보와 라이선스 미정)
     */
    public record Card(String cardId, int seq, @Nullable String standard, String dialect, String scriptKey,
                       TestDefinition.GuideF0 guideF0, @Nullable String referenceAudioPath) {
    }
}
