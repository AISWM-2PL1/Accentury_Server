-- V22 KAN-360 단어 문항 시간 초과 답안 - word_attempt_answer.choice_id를 null 허용으로 바꾼다.
--
-- V7은 적용된 순간 불변이므로(KAN-220 규칙) 새 파일로만 바꾼다.
--
-- 앱은 학습의 단어 문항에 5초 제한을 두고, 시간 안에 보기를 고르지 않으면 「고르지 않음」을 답안으로 보낸다
-- (명세서 §3.16). 서버는 그 문항을 오답으로 저장하되 고른 보기가 없으므로 choice_id가 null이다. 시간 초과
-- 여부는 choice_id is null로 판별하고 열을 더하지 않는다 - 시간 초과이면서 보기가 있는 행은 없으므로 열이 둘이면
-- 어긋날 수 있는 상태만 생긴다. 시간 초과 행은 늘 오답이다 (is_correct = false).
--
-- NOT NULL을 푸는 것은 카탈로그만 고치는 일이라 표를 다시 쓰지 않고, 기존 행은 그대로 읽힌다. 계정별 오답
-- (word_wrong_answer)에는 보기 열이 없어 바꿀 것이 없다. 레벨테스트 어휘 답안(vocab_answer)은 대상이 아니다.
alter table word_attempt_answer alter column choice_id drop not null;

-- 시간 초과(보기 없음)인데 정답으로 저장된 행을 막는다 (PR #76 리뷰). NOT NULL을 풀면서 DB가 지키던 것이 줄어, 엔티티
-- 생성자를 거치지 않는 쓰기(수동 SQL, 이관)가 그런 행을 넣을 수 있기 때문이다. 기존 행은 전부 choice_id가 있어 통과한다.
alter table word_attempt_answer
    add constraint ck_word_attempt_answer_timeout_wrong check (choice_id is not null or not is_correct);
