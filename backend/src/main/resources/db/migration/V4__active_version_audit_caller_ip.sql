-- V4 KAN-244 활성 버전 전환 감사에 호출 IP를 더한다.
--
-- 관리자 토큰은 공유 시크릿이라 감사 행이 "누가" 바꿨는지 가리지 못한다. 대신 어디서 왔는지를 남긴다
-- (backend ClientIps가 정한 값, IPv6 최대 45자). 이 열이 생기기 전의 행은 null로 남는다 - 소급할 값이 없다.
--
-- nullable 열 추가라 기존 행을 다시 쓰지 않고, 테이블도 운영자 전환 횟수만큼만 행이 있어 잠금이 짧다.
alter table active_version_audit add column caller_ip varchar(45);
