-- KAN-264: 억양 학습 발행본 - 코스와 대사 카드 (명세서 §3.17).
--
-- word_learning_definition(V7)과 같은 방식이다 - 마이그레이션의 INSERT로만 들어오고 발행 후 불변이며,
-- 본문 JSON(코스, 카드, 사투리 대사, scriptKey, 가이드 곡선, 기준 음원 경로)을 통째로 둔다. 활성 포인터
-- 행은 두지 않는다 - 서버가 발행 시각이 가장 늦은 행을 목록과 상세에 쓴다 (§3.17).
-- 권역은 dialect 열로 남겨 확장 여지를 둔다 (지금은 GYEONGNAM만 발행할 수 있다).
--
-- 계정별 기록은 이 마이그레이션에 없다 - 카드별 시도 기록은 억양 채점(KAN-267), 진도는 KAN-268이다.
create table intonation_learning_definition (
    content_version varchar(40) not null,
    dialect         varchar(20) not null,
    body            text        not null,
    published_at    timestamp(6) with time zone not null,
    constraint pk_intonation_learning_definition primary key (content_version)
);
