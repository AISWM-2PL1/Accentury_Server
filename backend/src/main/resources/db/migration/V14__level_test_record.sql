-- KAN-268: 학습 진도와 홈 상태 - 계정의 레벨테스트 최근 등급 기록 (명세서 §3.20, §5.5).
--
-- 홈(H-1)은 계정이 레벨테스트를 마쳤는지로 Primary 카드를 바꾸고, 학습 목록의 추천은 최근 등급에 맞는 레벨에서
-- 고른다. 세션과 결과는 완주 24시간 뒤 행째 지워지므로(SessionService.purgeExpired, §5.5) 그것으로는 판정할 수 없다.
-- 그래서 계정 귀속 세션의 결과가 확정될 때 계정당 1행을 덮어쓴다 (2026-10-11 결정). 완료 시각이 더 늦을 때만 덮는다.
--
-- 등급만 남긴다. 점수와 문항별 기록은 남기지 않는다 - 판정과 추천에 등급 순위 하나면 되고, 개인 결과를 영속으로
-- 늘리지 않기 위해서다. tier_code/tier_name/tier_rank/tier_count는 test_result의 같은 이름 열과 같은 값이다 (§3.7 tier).
--
-- app_user에 FK를 건다. 탈퇴는 계정 행을 지우지 않고(V2) 이 행을 같은 트랜잭션에서 물리 삭제하므로(§3.14)
-- ON DELETE 규칙은 기본(NO ACTION)이다. 웹 익명 세션은 이 표에 닿지 않는다.
create table level_test_record (
    user_id       uuid        not null,
    test_version  varchar(40) not null,
    score_version varchar(20) not null,
    tier_code     varchar(40) not null,
    tier_name     varchar(60) not null,
    tier_rank     integer     not null,
    tier_count    integer     not null,
    completed_at  timestamp(6) with time zone not null,
    constraint pk_level_test_record primary key (user_id),
    constraint fk_level_test_record_user foreign key (user_id) references app_user (id)
);
