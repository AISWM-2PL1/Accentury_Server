-- KAN-269: 음성 저장(학습 활용) 선택 동의와, 계정과 저장된 음성의 대응표.
--
-- 음성 저장 동의는 개인정보 수집 이용 동의(privacy_consent_at)와 별개의 선택 항목이다. 동의하지 않아도
-- 세션을 만들고 테스트를 볼 수 있고, 동의한 세션의 음성만 학습 버킷에 남는다 (training 패키지).
-- 앱은 계정에, 웹 익명 세션은 세션에 기록한다.

-- 앱 계정의 동의. 세 열이 모두 null이면 한 번도 동의하지 않은 계정이다.
-- 철회하면 withdrawn_at만 찍고 버전과 동의 시각은 이력으로 남긴다. 다시 동의하면 withdrawn_at을 지운다.
alter table app_user
    add column voice_consent_version      varchar(32),
    add column voice_consent_at           timestamp(6) with time zone,
    add column voice_consent_withdrawn_at timestamp(6) with time zone;

-- 웹 익명 세션의 동의. 세션 생성 요청(명세서 §3.1)의 voiceConsentVersion이 서버 게시 버전과 같을 때만 채운다.
-- 계정 세션은 이 열을 쓰지 않는다 - 업로드 시점에 계정의 동의를 본다(세션 도중의 철회가 바로 반영된다).
alter table test_session
    add column voice_consent_version varchar(32),
    add column voice_consent_at      timestamp(6) with time zone;

-- 계정과 저장된 음성의 대응표. 학습 버킷의 객체 키에는 세션 ID만 있고 test_session 행은 만료되면 삭제되므로,
-- 이 표가 없으면 만료 뒤에는 음성이 어느 계정 것인지 알 수 없다. 동의 철회와 탈퇴 때 그 계정의 음성을
-- 찾는 데 쓴다. 계정 세션의 음성을 처음 저장할 때 한 행을 남기고, 세션 만료 삭제와 무관하게 보관한다.
-- 그래서 test_session에 FK를 걸지 않는다. app_user에도 걸지 않는다 - 탈퇴는 행을 지우지 않고 PII 열만
-- 비우므로(V2, KAN-241) 탈퇴 뒤에도 user_id는 유효하고, 이 행은 탈퇴 뒤에도 남긴다 (2026-10-04 결정).
-- 웹 익명 세션은 계정이 없어 대상이 아니다.
create table training_voice_owner (
    session_id varchar(40) not null,
    user_id    uuid        not null,
    created_at timestamp(6) with time zone not null,
    constraint pk_training_voice_owner primary key (session_id)
);

create index ix_training_voice_owner_user on training_voice_owner (user_id);
