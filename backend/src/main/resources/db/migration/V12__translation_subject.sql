-- KAN-266: 번역 기록의 계정별 대체 ID 대응표.
--
-- prod 번역 기록 버킷(accentury-translator-prompt-<계정 ID>)의 객체에는 계정 ID 대신 이 표의 subject_id만 적는다
-- (명세서 §3.18). 버킷을 읽는 팀원은 이 표를 함께 보지 않는 한 누구의 기록인지 알 수 없다. 계정이 처음 기록될 때
-- 한 행을 만들고, 회원 탈퇴(§3.14)가 계정 파기와 같은 트랜잭션에서 그 행을 지운다 - S3 객체는 남고 그 뒤로는
-- 누구의 기록인지 알 수 없게 된다 (2026-10-08 결정).
--
-- app_user에 FK를 걸지 않는다 - 탈퇴는 계정 행을 지우지 않고 PII 열만 비우므로(V2, KAN-241) FK가 있어도 행 삭제를
-- 막아 주지 못하고, 이 표의 행은 탈퇴 코드가 직접 지운다. training_voice_owner(V6)와 같은 사정이다.
-- 기록 버킷이 없는 환경(staging)에서는 행이 생기지 않는다.
create table translation_subject (
    user_id    uuid not null,
    subject_id uuid not null,
    created_at timestamp(6) with time zone not null,
    constraint pk_translation_subject primary key (user_id),
    constraint ux_translation_subject_subject unique (subject_id)
);
