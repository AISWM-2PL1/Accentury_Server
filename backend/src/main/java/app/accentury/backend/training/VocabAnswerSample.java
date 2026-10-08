package app.accentury.backend.training;

import java.time.Instant;

/**
 * 레벨테스트 단어 답안 1건의 정오 기록 - 메타 JSON 1개가 된다 (KAN-276, 단어 난이도 측정용).
 * <p>
 * 음성 저장 동의와 상관없이 실사용자 세션의 답안이면 남긴다. 음성이 없는 기록이라 음성 학습 샘플의 라벨 전용 건과
 * 같은 {@value TrainingSample#NO_AUDIO_SEGMENT} 트리에 쓰고, 조각 순서도 같다 - 접두 하나를 나열하면 그 버전의 음성
 * 라벨과 단어 정오가 함께 나온다 ({@link #key}). 계정 id와 동의 기록은 싣지 않는다.
 *
 * @param answerId        {@code vocab_answer.id} - 단어는 재제출이 409라 문항당 1건이고 키가 겹치지 않는다.
 * @param region          출신 지역 코드 10개 중 하나 또는 {@code UNKNOWN}.
 * @param correctChoiceId 세션 testVersion 정의의 정답 - 정의를 몰라도 JSON만으로 정오를 다시 셀 수 있게 싣는다.
 * @param answeredAt      답안 저장 시각 ({@code vocab_answer.created_at}).
 */
public record VocabAnswerSample(
        String answerId,
        String sessionId,
        String itemId,
        String region,
        String testVersion,
        String scoreVersion,
        String choiceId,
        String correctChoiceId,
        boolean correct,
        Instant answeredAt) {

    /** 기록 종류 - 음성 메타 JSON의 {@code itemType} VOICE와 한 테이블에서 가른다. */
    public static final String ITEM_TYPE = "VOCABULARY";

    /** 객체 키 - {@code <env>/_no-audio/<region>/<testVersion>/<sessionId>/<itemId>/<answerId>.json}. */
    public String key(String envPrefix) {
        return envPrefix + "/" + TrainingSample.NO_AUDIO_SEGMENT + "/" + region + "/" + testVersion + "/"
                + sessionId + "/" + itemId + "/" + answerId + ".json";
    }
}
