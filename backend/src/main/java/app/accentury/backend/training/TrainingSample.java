package app.accentury.backend.training;

import org.jspecify.annotations.Nullable;

/**
 * 학습 샘플 1건 - WAV 1개와 메타 JSON 1개가 된다 (KAN-201 객체 규약).
 * <p>
 * 음성 저장에 동의하지 않은 익명 세션의 건은 <b>라벨 전용</b>이다 (KAN-274) - {@code audio}와 {@code consent}가 null이고
 * 메타 JSON 1개만 된다. 키는 환경 접두 바로 아래의 {@value #NO_AUDIO_SEGMENT} 조각으로 갈라, 음성 트리가 「언제나
 * WAV와 JSON 한 쌍」이라는 규약을 그대로 지키게 한다 ({@link #keyPrefix}).
 * <p>
 * 이름, 이메일 같은 개인 식별 정보는 없다. 다만 세션 ID와 작업 ID가 원문이고 계정 세션은 대응표
 * ({@link TrainingVoiceOwners})로 계정까지 이어진다 (KAN-269). 값은 전부 분석 요청({@code AnalysisRequest})과 AI 응답
 * (§4.1)에서 그대로 온 것이고, 점수는 KAN-200의 계수 전처리와 sv-0.3의 20점 환산 <b>이전</b>의
 * AI 원점수다 - {@code analysis_job.intonation_score}와 같은 값이다.
 *
 * @param region          출신 지역 코드 10개 중 하나 또는 {@code UNKNOWN} - 객체 키의 첫 조각과 같다.
 * @param scriptKey       문항 대본 키 - 더미 정의의 문항은 null이다.
 * @param durationMs      서버가 WAV에서 계산한 길이 (클라이언트 신고값이 아니다).
 * @param outcome         AI 응답으로 정한 종결 상태. 드물게 {@code analysis_job.status}와 다를 수 있다 - 스위퍼가
 *                        먼저 TIMED_OUT으로 종결한 작업의 늦은 응답은 조건부 UPDATE 0행으로 버려지지만 AI 원점수
 *                        자체는 유효해 그대로 남긴다 (Claude 검증자 리뷰, 의도).
 * @param intonationScore AI 원점수 0~100 - 성공에만 있다.
 * @param qualityCode     AI 응답 §4.1 값 - 성공에만 있다.
 * @param modelVersion    AI 응답 §4.1 값 - 성공에만 있다.
 * @param aiScoreVersion  AI 응답 §4.1의 scoreVersion - 성공에만 있다. 세션의 {@code scoreVersion}과 다른 값이다.
 * @param errorCode       판정 실패 코드(예: AUDIO_TOO_QUIET) - 판정 실패에만 있다.
 * @param correlationId   AI 호출 상관 ID - AI 로그와 대조용.
 * @param consent         이 세션의 음성 저장 동의 (KAN-269) - 라벨 전용 건(동의 없는 익명 세션, KAN-274)은 null이다.
 * @param audio           업로드 받은 WAV 바이트 그대로 (16kHz Mono 16-bit PCM, 최대 1MB) - 라벨 전용 건은 null이다.
 */
public record TrainingSample(
        String analysisJobId,
        String sessionId,
        String itemId,
        String region,
        @Nullable String scriptKey,
        String testVersion,
        String scoreVersion,
        long durationMs,
        Outcome outcome,
        @Nullable Integer intonationScore,
        @Nullable String qualityCode,
        @Nullable String modelVersion,
        @Nullable String aiScoreVersion,
        @Nullable String errorCode,
        String correlationId,
        @Nullable VoiceConsent consent,
        byte @Nullable [] audio) {

    /**
     * 라벨 전용 건의 키 조각 (KAN-274) - 환경 접두 바로 아래, 지역 조각 앞에 선다. 밑줄로 시작해 지역 코드
     * ({@code Region}의 대문자 이름)와 겹치지 않는다.
     */
    public static final String NO_AUDIO_SEGMENT = "_no-audio";

    public TrainingSample {
        // 음성과 동의는 함께 있거나 함께 없다 - 동의 없는 음성이 저장소까지 가는 조합을 타입 밖에서 막는다.
        if ((audio == null) != (consent == null)) {
            throw new IllegalArgumentException("음성과 음성 저장 동의는 함께 있거나 함께 없어야 한다");
        }
    }

    /** 음성까지 남기는 건인가 - false면 라벨 JSON만 남긴다 (동의 없는 익명 세션, KAN-274). */
    public boolean audioStored() {
        return audio != null;
    }

    /** 분석 작업 종결 상태와 같은 세 값 ({@code AnalysisJobStatus}의 종결 상태). */
    public enum Outcome { COMPLETED, FAILED, RETRYABLE_FAILED }

    /**
     * 객체 키 접두 - WAV와 JSON이 이 뒤에 확장자만 다르게 나란히 놓인다. 라벨 전용 건은 환경 접두 다음에
     * {@value #NO_AUDIO_SEGMENT} 조각이 하나 더 서고 JSON만 놓인다 - 그 뒤의 조각은 같은 순서다.
     *
     * @param envPrefix 환경 접두 ({@code staging} 또는 {@code prod}, KAN-269) - 두 환경이 버킷 하나를 나눠 쓴다.
     */
    public String keyPrefix(String envPrefix) {
        String root = audioStored() ? envPrefix : envPrefix + "/" + NO_AUDIO_SEGMENT;
        return root + "/" + region + "/" + testVersion + "/" + sessionId + "/" + itemId + "/" + analysisJobId;
    }
}
