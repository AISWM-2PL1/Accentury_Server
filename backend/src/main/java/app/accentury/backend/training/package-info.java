/**
 * 학습 음성 수집 (KAN-201, KAN-269).
 * <p>
 * 원본 음성은 기본적으로 요청 처리 중에만 메모리에 있고 영속 저장소에 남지 않는다. 예외는 음성 저장에
 * <b>선택 동의한 세션</b>이다 - 그 세션의 음성 WAV와 AI 원점수, 출신 지역을 모델 재학습용으로 S3에 보존한다.
 * 동의는 앱이면 계정에, 웹 익명이면 세션에 기록하고({@link app.accentury.backend.training.VoiceConsents}),
 * 동의하지 않아도 테스트 이용에는 제한이 없다. staging과 prod가 음성 전용 버킷 하나를 환경 접두로 나눠 쓴다.
 * <p>
 * 계정 세션의 음성은 대응표({@link app.accentury.backend.training.TrainingVoiceOwners})로 계정까지 이어진다 -
 * 철회나 탈퇴 때 그 계정의 음성을 찾기 위해서다. 저장된 객체를 지우는 코드는 이 패키지에 없다.
 * <p>
 * 켜는 스위치는 {@code accentury.training.bucket}이고(SSM {@code ACCENTURY_TRAINING_BUCKET}), 없으면
 * {@link app.accentury.backend.training.TrainingSampleStore#NONE}이 자리를 채워 저장 코드가 호출되지 않는다.
 * 동의 기록 자체는 버킷이 없는 환경에서도 남는다.
 */
@NullMarked
package app.accentury.backend.training;

import org.jspecify.annotations.NullMarked;
