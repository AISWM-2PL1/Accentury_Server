/**
 * staging 전용 학습 데이터 수집 (KAN-201).
 * <p>
 * 원본 음성은 요청 처리 중에만 메모리에 있고 영속 저장소에 남지 않는다 (FR-DP-01) - 그래서 모델을 다시
 * 학습시킬 실발화 데이터가 어디에도 없었다. 2026-09-08 결정으로 <b>staging에만</b> S3 버킷을 두고 내부
 * 테스터의 음성 WAV와 AI 원점수, 출신 지역을 보존한다. prod는 버킷도 권한도 설정도 없어 FR-DP-01이
 * 그대로 성립한다. 켜는 스위치는 {@code accentury.training.bucket} 하나이고(SSM
 * {@code ACCENTURY_TRAINING_BUCKET}), 없으면 {@link app.accentury.backend.training.TrainingSampleStore#NONE}이
 * 자리를 채워 저장 코드가 호출되지 않는다.
 * <p>
 * 버킷이 켜져 있어도 저장 대상은 학습 활용에 동의한 테스터 계정의 세션뿐이다 (KAN-239). staging은 인터넷에
 * 공개돼 있어 "staging이니 내부 테스터"가 성립하지 않는다. 세션 ID는 HMAC 가명(speaker)으로 바뀌어 DB와 로그의
 * 세션 ID와 이어지지 않고, 버킷은 수명주기 만료일(동의서의 보유 기간)과 학습 읽기 역할 전용 버킷 정책을 갖는다.
 */
@NullMarked
package app.accentury.backend.training;

import org.jspecify.annotations.NullMarked;
