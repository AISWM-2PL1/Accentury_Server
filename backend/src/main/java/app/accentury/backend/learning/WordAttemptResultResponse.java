package app.accentury.backend.learning;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * {@code POST .../complete}의 200 응답 (명세서 §3.16, W-5) - 정답률과 오답 목록.
 *
 * @param accuracyPercent {@code correctCount x 100 / itemCount}를 반올림한 정수
 * @param stars           이번 시도의 정답률로 낸 별 1~3 (KAN-359, 결과 화면의 「이번 스탬프」 별) - 세트 목록의 {@code stars}는
 *                        최고 기록 기준이라 이 값보다 클 수 있다
 * @param wrongItems      틀린 문항 - 문항 seq 순
 */
public record WordAttemptResultResponse(String attemptId, String contentVersion, String setId, int itemCount,
                                        int correctCount, int accuracyPercent, int stars, List<WrongItem> wrongItems,
                                        Instant completedAt) {

    /** 정답 수에서 정답률과 이번 시도의 별을 채운다. */
    static WordAttemptResultResponse of(String attemptId, String contentVersion, String setId, int itemCount,
                                        int correctCount, List<WrongItem> wrongItems, Instant completedAt) {
        int accuracy = accuracyPercent(correctCount, itemCount);
        return new WordAttemptResultResponse(attemptId, contentVersion, setId, itemCount, correctCount, accuracy,
                LearningStars.stars(accuracy), wrongItems, completedAt);
    }

    /**
     * 오답 한 건 - 고른 보기와 정답, 해설을 함께 준다 (복습 안내는 FE 문구다).
     *
     * @param chosenChoiceId 고른 보기 - 시간 초과 문항이면 null
     * @param chosenText     고른 보기의 낱말 - 시간 초과 문항이면 null
     * @param timedOut       제한 시간 안에 보기를 고르지 않은 문항인지 (KAN-360)
     */
    public record WrongItem(String itemId, String cardId, String prompt, @Nullable String chosenChoiceId,
                            @Nullable String chosenText, boolean timedOut, String correctChoiceId,
                            String correctText, String explanation) {
    }

    static int accuracyPercent(int correctCount, int itemCount) {
        return (int) Math.round(correctCount * 100.0 / itemCount);
    }
}
