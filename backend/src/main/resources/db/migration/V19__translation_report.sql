-- V19 KAN-343 번역 결과 신고 - 사투리 번역기의 결과가 부적절하다고 사용자가 알린 기록.
--
-- 번역기는 외부 LLM이 문장을 만든다. 부적절한 결과를 사용자가 알릴 길이 있어야 하고(App Store 심사 지침 1.2),
-- 운영자는 신고가 저장되면 슬랙 알림으로 본다 (backend translation 패키지의 TranslationReportSlackNotifier).
--
-- 번역 기록(prod의 S3 객체, KAN-266)과 따로 둔다. 번역 기록은 staging에 없고 계정 대신 대체 ID만 적혀 있어, 신고를
-- 그 기록의 식별자로 가리킬 수 없다. 그래서 신고는 원문과 결과를 클라이언트가 보낸 그대로 싣는다.
--
-- user_id는 신고한 계정이다 (게스트 계정 포함). FK를 걸지 않고 null을 허용한다 - 탈퇴와 게스트 계정 파기가 이 열만
-- 비워 계정과의 연결을 끊고, 신고 내용은 보존 기간(1년)까지 남긴다 (2026-11-10 결정, 번역 기록의 대체 ID와 같은
-- 처리). 보존 기간이 지난 행은 정리 잡이 지운다 (TranslationReportRetention).
--
-- 길이:
--   source_text 100 - 번역 입력의 상한과 같다 (명세서 §3.18의 MAX_INPUT_LENGTH).
--   result_text 300 - 번역 결과는 한 문장이라 입력의 세 배면 넉넉하다. 더 긴 값은 backend가 400으로 거절한다.
--   direction 16    - toDialect | toStandard (TranslationDirection의 apiValue).
create table translation_report (
    id          uuid         not null,
    user_id     uuid,
    direction   varchar(16)  not null,
    source_text varchar(100) not null,
    result_text varchar(300) not null,
    created_at  timestamp(6) with time zone not null,
    constraint pk_translation_report primary key (id),
    constraint ck_translation_report_direction check (direction in ('toDialect', 'toStandard'))
);

-- 보존 기간 삭제가 created_at으로 탄다.
create index ix_translation_report_created_at on translation_report (created_at);
-- 탈퇴가 그 계정의 신고를 찾아 연결을 끊는다. 연결이 끊긴 행(null)은 담지 않는 부분 색인이다.
create index ix_translation_report_user on translation_report (user_id) where user_id is not null;
