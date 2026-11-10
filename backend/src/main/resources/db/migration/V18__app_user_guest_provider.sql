-- V18 KAN-329 게스트 계정 - app_user.provider의 허용 값에 GUEST를 더한다.
--
-- V2는 적용된 순간 불변이므로(KAN-220 규칙) 새 파일로만 바꾼다.
--
-- 게스트 계정은 소셜 로그인 없이 서버가 발급하는 계정이다 (명세서 §3.21, App Store 심사 지침 5.1.1(v)).
-- 앱의 「로그인 없이 둘러보기」가 만들고 사투리 번역기만 쓴다. IdP가 없으므로 provider_user_id에는 서버가 만든
-- 난수(UUID)가 들어간다 - 유일 제약 (provider, provider_user_id)은 그대로 성립한다. 이메일, 이름, 생년월일,
-- 성별, 출신지역은 전부 null이고 프로필은 완료되지 않는다. 방침 동의는 소셜 계정과 같이 발급 때 받으므로
-- privacy_consent_at과 privacy_policy_version의 NOT NULL은 그대로다.
--
-- provider varchar(10)에 'GUEST'(5자)가 들어간다. 제약을 다시 만드는 동안 app_user 전체를 검증 스캔한다 -
-- 계정 수가 적어 그대로 둔다.
alter table app_user drop constraint ck_app_user_provider;
alter table app_user
    add constraint ck_app_user_provider check (provider in ('GOOGLE', 'KAKAO', 'NAVER', 'APPLE', 'GUEST'));

-- 살아 있는 게스트 계정의 id 색인 - 게스트 계정 정리(backend GuestAccountRetention)가 id 순서로 전부를 훑는다.
-- 파기된 게스트 행(deleted_at이 찍힌 행)은 지우지 않고 남기므로 시간이 갈수록 그쪽이 훨씬 많아진다. 부분 색인이라
-- 그 행들은 색인에 없고, 정리 쿼리는 살아 있는 게스트 수만큼만 읽는다. 조건은 쿼리의 where와 글자 그대로 같아야
-- 색인이 쓰인다 (AppUserRepository.findLiveGuestIdsCreatedBefore).
create index ix_app_user_live_guest on app_user (id) where provider = 'GUEST' and deleted_at is null;
