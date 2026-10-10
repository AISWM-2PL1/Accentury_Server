-- KAN-267: 억양 학습 채점 - 계정의 카드별 녹음 시도 (명세서 §3.19, §5.5).
--
-- 학습은 로그인 필수라(2026-10-04 결정) 시도는 전부 계정(app_user) 기준이다. 웹 익명 세션은 이 표에 닿지 않는다.
-- 레벨테스트의 analysis_job과 따로 두는 이유는 수명이 달라서다 - analysis_job은 세션(test_session)에 FK로 묶여
-- 24시간 뒤 지워지지만, 이 행은 직전 대비 점수와 진도(KAN-268)의 입력이라 탈퇴 때까지 남는다.
--
-- 행 하나가 업로드 1건이자 AI 분석 1건이다. 상태와 오류 코드는 analysis_job과 같은 값을 쓴다
-- (PROCESSING, COMPLETED, RETRYABLE_FAILED, FAILED) - 같은 전달 큐와 같은 종결 규칙을 지나기 때문이다.
-- 음성은 어디에도 없다. 음성 저장에 동의한 계정만 음성 버킷의 학습 트리에 남고, 그 대응은 training_voice_owner의
-- session_id 열에 이 표의 id를 넣어 잇는다 (2026-10-10 결정).
--
-- raw_score는 AI 원점수(0~100), score는 사용자에게 보이는 변환 점수다(억양 전처리 + 100점 처리, §3.19).
-- pitch_feedback은 모델 order 항목을 옮긴 JSON 배열이다 - 행이 결과의 정본이라 폴링이 언제든 같은 본문을 받는다.
--
-- app_user에 FK를 건다. 탈퇴는 계정 행을 지우지 않고(V2) 이 표의 행을 같은 트랜잭션에서 물리 삭제하므로
-- (§3.14) ON DELETE 규칙은 기본(NO ACTION)이다. 발행본 FK는 "발행되지 않은 버전의 시도"를 막는다.
create table intonation_learning_attempt (
    id              varchar(40)  not null,
    user_id         uuid         not null,
    content_version varchar(40)  not null,
    card_id         varchar(40)  not null,
    idempotency_key varchar(100) not null,
    status          varchar(20)  not null,
    created_at      timestamp(6) with time zone not null,
    started_at      timestamp(6) with time zone,
    finished_at     timestamp(6) with time zone,
    raw_score       integer,
    score           integer,
    pitch_feedback  text,
    quality_code    varchar(40),
    error_code      varchar(40),
    model_version   varchar(60),
    score_version   varchar(40),
    constraint pk_intonation_learning_attempt primary key (id),
    constraint fk_intonation_learning_attempt_user foreign key (user_id) references app_user (id),
    constraint fk_intonation_learning_attempt_definition foreign key (content_version)
        references intonation_learning_definition (content_version),
    -- 멱등 재전송 판별 (§5.2) - 같은 계정, 같은 카드, 같은 키는 시도 하나다.
    constraint ux_intonation_learning_attempt_key unique (user_id, content_version, card_id, idempotency_key)
);

-- 직전 대비(같은 카드의 최근 완료 시도)와 탈퇴 파기가 계정과 카드로 찾는다.
create index ix_intonation_learning_attempt_user_card
    on intonation_learning_attempt (user_id, content_version, card_id, created_at);

-- 혼잡 판정과 타임아웃 스위퍼가 PROCESSING만 센다 - analysis_job의 ix_analysis_job_processing과 같은 부분 인덱스다.
create index ix_intonation_learning_attempt_processing
    on intonation_learning_attempt (created_at) where status = 'PROCESSING';
