-- KAN-284: 결과 화면 대기 시간 지표(accentury.result.wait)의 시작점 - 세션의 첫 /complete 호출 시각 (명세서 §3.6).
--
-- 대기 화면은 /complete를 폴링하므로 "세션의 첫 /complete 호출"이 곧 결과 화면 진입이다. 완주 판정이 PROCESSING 또는
-- READY인 첫 호출이 세션 행 잠금 트랜잭션 안에서 null일 때만 채운다. 422(미제출)와 409(재녹음)는 예외로 롤백되므로
-- 쓰이지 않는다. READY를 확정하는 호출이 now - complete_requested_at을 지표로 남긴다.
--
-- 태스크 메모리에 두지 않는 이유는 backend 태스크가 여럿이라 폴링이 다른 태스크로 가기 때문이다. 이미 잠근 행에
-- 쓰므로 추가 조회가 없다. 기존 행은 null이다. 세션 행과 함께 만료 정리로 지워진다.
alter table test_session
    add column complete_requested_at timestamp(6) with time zone;
