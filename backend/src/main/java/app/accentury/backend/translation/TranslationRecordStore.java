package app.accentury.backend.translation;

/**
 * 번역 기록을 보존하는 곳 (KAN-266, 2026-10-08 결정 - prod만). 구현은 예외를 밖으로 내지 않고 응답을 기다리게 하지
 * 않는다 - 기록 실패가 번역 응답에 닿으면 안 된다.
 * <p>
 * 기록 버킷이 없는 배포(staging, 로컬, 테스트)는 {@link #NONE}이다 - 대체 ID도 만들지 않는다.
 */
@FunctionalInterface
public interface TranslationRecordStore {

    /** 아무것도 저장하지 않는다 - {@code accentury.translation.record-bucket}이 없는 배포의 자리. */
    TranslationRecordStore NONE = record -> {
    };

    void save(TranslationRecord record);
}
