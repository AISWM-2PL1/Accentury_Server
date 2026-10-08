package app.accentury.backend.learning;

/**
 * {@code POST .../items/{itemId}/answer}의 200 응답 (명세서 §3.16, W-4) - 레벨테스트(§3.5)와 달리 정오와 정답, 해설을
 * 즉시 준다 (FR-WD-03).
 *
 * @param correct         채점 결과
 * @param correctChoiceId 정답 보기
 * @param correctText     정답 보기의 낱말
 * @param explanation     해설
 * @param answeredCount   이 시도에서 답안이 저장된 문항 수 - 재전송 응답에서는 현재 값이다
 * @param itemCount       세트 문항 수
 */
public record WordAnswerResponse(boolean correct, String correctChoiceId, String correctText, String explanation,
                                 int answeredCount, int itemCount) {
}
