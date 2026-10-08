package app.accentury.backend.learning;

import java.util.List;

/**
 * {@code GET /v0/learning/word-sets}의 200 응답 (명세서 §3.16, W-1).
 * <p>
 * 완료 표시와 추천은 아직 없다 - 학습 진도 티켓(KAN-268)이 필드를 더한다.
 *
 * @param contentVersion 목록이 속한 발행본
 * @param sets           세트 요약 - seq 오름차순
 */
public record WordSetListResponse(String contentVersion, List<Summary> sets) {

    /** 세트 요약 한 줄. */
    public record Summary(String setId, int seq, int level, String category, String title,
                          int cardCount, int itemCount) {
    }
}
