package app.accentury.backend.training;

import org.jspecify.annotations.Nullable;

/**
 * 억양 학습 녹음 1건의 음성 저장 단위 (KAN-267, 명세서 §3.19) - 음성 버킷의 학습 트리에 WAV 1개와 메타 JSON 1개로 남는다.
 * <p>
 * 레벨테스트 샘플({@link TrainingSample})과 따로 둔 것은 세션이 없어서다 - 세션, 문항 대신 학습 시도와 카드가 키가
 * 되고, 키는 {@code _learning} 트리에 들어간다 (2026-10-10 결정). 음성 저장에 동의한 계정의 건만 만든다 - 학습에는
 * 라벨 전용 저장이 없다. 점수는 AI 원점수다 (사용자에게 보인 변환 점수가 아니다).
 *
 * @param attemptId       학습 시도 id ({@code la_...}) - 대응표({@link TrainingVoiceOwners})의 {@code session_id} 열에 들어간다
 * @param region          계정의 출신 지역 코드 또는 {@code UNKNOWN} - 키의 조각이다
 * @param contentVersion  억양 학습 발행본 버전
 * @param scoreVersion    학습 점수 변환의 점수 버전 (AI 요청과 응답의 값)
 * @param outcome         AI 응답으로 정한 종결 상태
 * @param audio           업로드 받은 WAV 바이트 그대로 - 호출부가 저장 뒤 0으로 덮는다
 */
public record LearningVoiceSample(
        String attemptId,
        String cardId,
        String region,
        String scriptKey,
        String contentVersion,
        String scoreVersion,
        long durationMs,
        TrainingSample.Outcome outcome,
        @Nullable Integer intonationScore,
        @Nullable String qualityCode,
        @Nullable String modelVersion,
        @Nullable String errorCode,
        String correlationId,
        VoiceConsent consent,
        byte[] audio) {

    /** 음성 트리 안의 학습 하위 트리 이름 - 레벨테스트의 지역 조각({@code SEOUL} 등)과 겹치지 않게 밑줄로 시작한다. */
    public static final String LEARNING_SEGMENT = "_learning";

    /** 메타 JSON의 {@code itemType} - 레벨테스트 음성(VOICE)과 가른다. */
    public static final String ITEM_TYPE = "LEARNING_VOICE";

    /** 키 앞부분 - 뒤에 {@code .wav}나 {@code .json}을 붙인다. 재녹음은 새 시도라 덮어쓰지 않는다. */
    public String keyPrefix(String envPrefix) {
        return envPrefix + "/" + LEARNING_SEGMENT + "/" + region + "/" + contentVersion + "/" + cardId + "/" + attemptId;
    }

    /** 바이트와 동의는 찍지 않는다 - 음성과 계정 id가 로그에 실리면 안 된다 (KAN-240). */
    @Override
    public String toString() {
        return "LearningVoiceSample[attemptId=" + attemptId + ", cardId=" + cardId + ", outcome=" + outcome + "]";
    }
}
