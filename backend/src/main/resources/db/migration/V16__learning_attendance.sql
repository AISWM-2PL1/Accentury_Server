-- KAN-286: 홈의 연속 출석 - 계정의 출석일 기록 (명세서 §3.20, §5.5).
--
-- 출석 1일은 계정이 그날(Asia/Seoul 자정 경계) 레벨테스트 완주, 단어 세트 완료, 억양 카드 채점 완료 가운데 하나를 한
-- 날이다 (2026-10-14 결정). 세 기록은 각자 다른 표에 있고 세션과 결과는 24시간 뒤 지워지므로, 날짜별로 집계하지 않고
-- 출석일을 따로 남긴다. 보상 체계(KAN-257)도 같은 기록을 읽는다.
--
-- 계정과 날짜만 남긴다. 무엇을 했는지와 시각은 남기지 않는다 - 연속 일수에는 날짜면 되고, 개인 기록을 영속으로 늘리지
-- 않기 위해서다. 같은 날 여러 번 해도 기본 키로 1행이다. 연속 일수는 계정의 최근 날짜부터 거슬러 읽으므로 기본 키
-- (user_id, attended_on) 인덱스로 충분하다.
--
-- app_user에 FK를 건다. 탈퇴는 계정 행을 지우지 않고(V2) 이 행들을 같은 트랜잭션에서 물리 삭제하므로(§3.14)
-- ON DELETE 규칙은 기본(NO ACTION)이다. 웹 익명 세션은 이 표에 닿지 않는다.
create table learning_attendance (
    user_id     uuid not null,
    attended_on date not null,
    constraint pk_learning_attendance primary key (user_id, attended_on),
    constraint fk_learning_attendance_user foreign key (user_id) references app_user (id)
);
