-- V2 KAN-223 계정 - 앱 소셜 로그인 사용자(app_user)와 세션의 계정 귀속(test_session.user_id).
--
-- 재베이스라인(KAN-220) 뒤 첫 스키마 변경이다. V1은 적용된 순간 불변이므로 새 파일로만 바꾼다.
--
-- 계정은 앱(Android, iOS)만 만든다 - 웹은 익명 유지이고 앞으로도 로그인을 넣지 않는다 (명세서 §2.1).
-- 계정 식별은 (provider, provider_user_id)이고 이메일은 유일 제약이 없다 - 같은 이메일로 다른 IdP에
-- 로그인하면 별개 계정이다 (계정 연동 FR-AC-04는 범위 밖, 명세서 §3.9).
--
-- 프로필 완료(profile_completed_at)는 email, name, birth_date, gender, region 다섯 열이 전부 있을 때만
-- 찍힌다. 판정은 backend(AppUser)가 하고, 이 열은 판정 결과의 기록이다. nickname과 profile_image_url은
-- IdP가 줄 때만 남기는 보조 정보라 판정에 쓰지 않는다.
--
-- 길이:
--   provider_user_id 255 - 구글과 애플 sub는 불투명 문자열(애플은 44자 안팎), 카카오는 숫자, 네이버는 64자 안팎이다.
--   email 254       - RFC 5321 경로 상한. IdP 값이 이보다 길면 backend가 버리고 사용자가 입력한다.
--   name 50         - 추가 정보 화면의 상한(명세서 §3.10). IdP 값은 이 길이로 자른다.
--   nickname 100, profile_image_url 1024 - IdP 보조 정보. 넘치면 버린다.
--   privacy_policy_version 32 - 동의한 개인정보처리방침 버전 문자열 (예: 2026-09-24).
--
-- 개인 식별 정보가 들어가는 첫 테이블이다. 로그에는 이 테이블의 이메일, 이름, 생년월일을 남기지 않는다
-- (명세서 §2.6, NFR-SC-07). 탈퇴(FR-AC-09)는 별도 티켓이고 deleted_at은 그 자리다 - 지금은 아무도 쓰지 않고,
-- 값이 있는 계정은 backend가 없는 사용자로 본다.
create table app_user (
    id                     uuid         not null,
    provider               varchar(10)  not null,
    provider_user_id       varchar(255) not null,
    email                  varchar(254),
    name                   varchar(50),
    birth_date             date,
    gender                 varchar(10),
    -- 출신지역 코드 10개 중 하나 (session.Region, KAN-202와 같은 목록). 계정으로 만든 세션의
    -- test_session.region은 요청 본문이 아니라 이 값이다 (명세서 §3.1).
    region                 varchar(16),
    nickname               varchar(100),
    profile_image_url      varchar(1024),
    profile_completed_at   timestamp(6) with time zone,
    privacy_consent_at     timestamp(6) with time zone not null,
    privacy_policy_version varchar(32)  not null,
    created_at             timestamp(6) with time zone not null,
    updated_at             timestamp(6) with time zone not null,
    deleted_at             timestamp(6) with time zone,
    constraint pk_app_user primary key (id),
    constraint ux_app_user_provider_subject unique (provider, provider_user_id),
    constraint ck_app_user_provider check (provider in ('GOOGLE', 'KAKAO', 'NAVER', 'APPLE')),
    constraint ck_app_user_gender check (gender in ('MALE', 'FEMALE'))
);

-- 세션의 계정 귀속 (명세서 §3.1, FR-AC-11). 웹 익명 세션과 로그인 전 앱의 세션은 null이다.
-- V1 주석의 "prod에서는 region이 항상 null"은 이 시점부터 맞지 않는다 - 계정 세션은 계정의 출신지역을 싣는다.
--
-- ON DELETE는 기본(NO ACTION)이다. 계정 행을 지우는 경로가 아직 없고(탈퇴는 별도 티켓), 탈퇴가 그 계정의
-- 세션을 어떻게 처리할지(함께 폐기인지 귀속만 끊는지)는 그 티켓이 정한다. 그때 이 제약을 바꾼다.
alter table test_session add column user_id uuid;
alter table test_session
    add constraint fk_test_session_user foreign key (user_id) references app_user (id);

-- FK 컬럼 인덱스 - PostgreSQL은 FK에 인덱스를 만들지 않는다. 계정 행을 지울 때(탈퇴) 참조 검사가
-- 순차 스캔을 타지 않게 한다. 익명 세션이 대부분이라 null은 담지 않는 부분 인덱스다.
create index ix_test_session_user on test_session (user_id) where user_id is not null;
