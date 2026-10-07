-- KAN-265: 단어 학습 - 발행본, 세트 학습 시도, 문항 답안, 계정별 오답 (명세서 §3.16, §5.5).
--
-- 학습은 로그인 필수라(2026-10-04 결정) 시도와 오답은 전부 계정(app_user) 기준이다. 웹 익명 세션은
-- 이 테이블들에 닿지 않는다.

-- 단어 학습 발행본. test_definition과 같은 방식이다 - 마이그레이션의 INSERT로만 들어오고 발행 후
-- 불변이며, 본문 JSON(세트, 카드, 문항, 정답, 해설)을 통째로 둔다. 활성 포인터 행은 두지 않는다 -
-- 서버가 발행 시각이 가장 늦은 행을 목록과 상세에 쓰고, 시도는 시작 시점의 버전에 고정된다 (§3.16).
-- 원천은 레벨테스트 어휘 풀(tools/content/vocabulary_gn.py)이고 학습용으로 따로 발행한다 (2026-10-07 결정).
create table word_learning_definition (
    content_version varchar(40) not null,
    dialect         varchar(20) not null,
    body            text        not null,
    published_at    timestamp(6) with time zone not null,
    constraint pk_word_learning_definition primary key (content_version)
);

-- 세트 학습 시도 = 한 계정이 세트 하나를 한 번 푸는 단위 (§3.16). 같은 세트를 다시 풀면 새 행이다.
-- completed_at이 null이면 진행 중이고, 완료 뒤에는 correct_count가 채워진다 - 완료 응답과 나중의
-- 진도 조회(KAN-268)가 답안 행을 다시 세지 않게 하려는 사본이다.
--
-- app_user에 FK를 건다. 탈퇴는 계정 행을 지우지 않고(V2) 이 표의 행을 같은 트랜잭션에서 물리 삭제하므로
-- (명세서 §3.14) ON DELETE 규칙은 기본(NO ACTION)이다. 발행본 FK는 "발행되지 않은 버전의 시도"를 막는다.
create table word_set_attempt (
    id              varchar(40) not null,
    user_id         uuid        not null,
    content_version varchar(40) not null,
    set_id          varchar(40) not null,
    item_count      integer     not null,
    correct_count   integer,
    started_at      timestamp(6) with time zone not null,
    completed_at    timestamp(6) with time zone,
    constraint pk_word_set_attempt primary key (id),
    constraint fk_word_set_attempt_user foreign key (user_id) references app_user (id),
    constraint fk_word_set_attempt_definition foreign key (content_version)
        references word_learning_definition (content_version)
);

-- 탈퇴 때 계정의 시도를 찾아 지우고, 진도 조회(KAN-268)가 계정의 완료 세트를 훑는다.
create index ix_word_set_attempt_user on word_set_attempt (user_id);

-- 문항 답안 = 시도 안 문항당 1행. vocab_answer와 같은 규칙이다 - (attempt_id, item_id) 유니크가
-- "문항당 답안은 하나"를 DB에서 강제하고, 제출은 시도 행 잠금으로 직렬화되므로 이 제약은 마지막 안전망이다.
-- 정오는 제출 시점에 발행본의 정답표와 대조해 확정한다 (발행본 불변). 시도가 지워지면 함께 지워진다.
create table word_attempt_answer (
    id              varchar(40)  not null,
    attempt_id      varchar(40)  not null,
    item_id         varchar(40)  not null,
    choice_id       varchar(40)  not null,
    is_correct      boolean      not null,
    idempotency_key varchar(100) not null,
    created_at      timestamp(6) with time zone not null,
    constraint pk_word_attempt_answer primary key (id),
    constraint fk_word_attempt_answer_attempt foreign key (attempt_id)
        references word_set_attempt (id) on delete cascade,
    constraint ux_word_attempt_answer_attempt_item unique (attempt_id, item_id)
);

-- 계정별 오답 (FR-WD-04). 틀린 답안이 저장될 때마다 (user_id, content_version, item_id) 행을 만들거나
-- 횟수와 시각을 갱신한다. 복습 API(M9, 범위 밖)가 읽을 자리라 시도가 아니라 계정에 매달고, 시도 행을
-- 지워도 남는다 - 탈퇴 때만 계정 파기와 같은 트랜잭션에서 지운다 (§3.14). 나중에 맞혀도 지우지 않는다 -
-- 복습 큐에서 빼는 규칙은 복습 티켓이 정한다. set_id는 복습 화면이 세트로 되돌아갈 때 쓰는 사본이다.
-- 발행본 FK는 시도 테이블과 같은 이유다 - 복습 API가 이 행의 문항을 발행본에서 찾으므로, 발행되지 않은 버전의
-- 오답이 들어오면 복습이 그 행에서 깨진다 (PR #30 리뷰).
create table word_wrong_answer (
    id              varchar(40) not null,
    user_id         uuid        not null,
    content_version varchar(40) not null,
    set_id          varchar(40) not null,
    item_id         varchar(40) not null,
    wrong_count     integer     not null,
    first_wrong_at  timestamp(6) with time zone not null,
    last_wrong_at   timestamp(6) with time zone not null,
    constraint pk_word_wrong_answer primary key (id),
    constraint fk_word_wrong_answer_user foreign key (user_id) references app_user (id),
    constraint fk_word_wrong_answer_definition foreign key (content_version)
        references word_learning_definition (content_version),
    constraint ux_word_wrong_answer_user_item unique (user_id, content_version, item_id)
);
