package app.accentury.backend.training;

/**
 * 억양 학습 녹음의 음성 저장소 (KAN-267, 명세서 §3.19). 버킷이 없는 배포(로컬, 테스트)는 {@link #NONE}이다.
 * <p>
 * 호출 시점과 실패 처리는 {@link TrainingSampleStore}와 같다 - 상태 전이가 끝난 뒤, 오디오 버퍼를 지우기 전에 동기로
 * 부르고, 구현은 예외를 밖으로 내지 않는다.
 */
@FunctionalInterface
public interface LearningVoiceStore {

    /** 아무것도 저장하지 않는다 - {@code accentury.training.bucket}이 없는 배포의 자리. */
    LearningVoiceStore NONE = sample -> {
    };

    void save(LearningVoiceSample sample);
}
