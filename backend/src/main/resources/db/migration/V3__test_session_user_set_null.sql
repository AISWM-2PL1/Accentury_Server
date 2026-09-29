-- V3 KAN-241 회원 탈퇴 - 세션의 계정 귀속 FK를 on delete set null로 바꾼다.
--
-- V2는 적용된 순간 불변이므로(KAN-220 규칙) 새 파일로만 바꾼다. V2 주석이 "탈퇴 티켓이 정한다"고 남긴 자리다.
--
-- 탈퇴는 소프트 삭제다 - app_user 행은 남기고 PII 열을 즉시 null로 덮으며, 그 계정 세션의 user_id도 같은
-- 트랜잭션에서 null로 끊는다 (backend WithdrawalService, 명세서 §3.14). 세션은 지우지 않는다 - 결과 조회가 계속
-- 세션 토큰으로 되고, 세션 자체는 기존 정리 작업이 24시간 뒤 지운다.
--
-- 이 제약이 실제로 일하는 것은 탈퇴 행을 물리 삭제하는 배치(별도 티켓)가 붙은 뒤다. 그 배치가 계정 행을 지울 때
-- 혹시 남은 세션 귀속(탈퇴 트랜잭션과 겹쳐 뒤늦게 만들어진 세션)이 삭제를 막지 않고 저절로 끊기게 한다.
--
-- 제약을 다시 만드는 동안 test_session 전체를 검증 스캔한다. 세션은 24시간 뒤 지워져 행이 적으므로 그대로 둔다.
alter table test_session drop constraint fk_test_session_user;
alter table test_session
    add constraint fk_test_session_user foreign key (user_id) references app_user (id) on delete set null;
