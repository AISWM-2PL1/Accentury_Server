package app.accentury.backend.learning;

import java.time.Instant;
import java.util.List;

/**
 * {@code POST .../complete}의 200 응답 (명세서 §3.16, W-5) - 정답률과 오답 목록.
 *
 * @param accuracyPercent {@code correctCount x 100 / itemCount}를 반올림한 정수
 * @param wrongItems      틀린 문항 - 문항 seq 순
 */
public record WordAttemptResultResponse(String attemptId, String contentVersion, String setId, int itemCount,
                                        int correctCount, int accuracyPercent, List<WrongItem> wrongItems,
                                        Instant completedAt) {

    /** 오답 한 건 - 고른 보기와 정답, 해설을 함께 준다 (복습 안내는 FE 문구다). */
    public record WrongItem(String itemId, String cardId, String prompt, String chosenChoiceId, String chosenText,
                            String correctChoiceId, String correctText, String explanation) {
    }

    static int accuracyPercent(int correctCount, int itemCount) {
        return (int) Math.round(correctCount * 100.0 / itemCount);
    }
}
