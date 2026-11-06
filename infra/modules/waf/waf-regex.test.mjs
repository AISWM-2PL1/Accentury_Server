// WAF 경로 정규식의 단위 테스트 (KAN-299).
//
// main.tf의 locals에서 정규식 문자열을 그대로 읽어 돌린다. 정규식이 틀리면 음성 업로드가 전부 403이
// 되는데 화면은 멀쩡해 원인을 찾기 어렵고(docs/ops/waf-edge.md), 반대로 넓으면 끝 세그먼트가 경로
// 변수인 GET 경로가 CommonRuleSet 본문 예외를 받는다 (PR #55 리뷰). 두 방향을 여기서 고정한다.
//
// AWS WAF는 URL_DECODE와 NORMALIZE_PATH를 거친 경로에 정규식을 적용하므로, 여기서도 디코드한 경로를
// 넣는다. 쓰는 문법은 문자 클래스, 그룹, 선택, 앵커뿐이라 JS 정규식과 AWS의 해석이 같다.
//
// `node --test 'infra/modules/waf/*.test.mjs'` 로 돈다 (edge 모듈의 테스트와 같은 방식, CI는 test.yml edge-test).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(HERE, 'main.tf'), 'utf8');

/** locals 블록의 `<name> = "<regex>"` 한 줄을 읽는다. HCL 문자열에 이스케이프가 없는 정규식만 쓴다. */
function local(name) {
  const match = source.match(new RegExp('^\\s*' + name + '\\s*=\\s*"([^"]+)"', 'm'));
  assert.ok(match, name + ' 정의를 main.tf에서 찾지 못했다');
  assert.ok(!match[1].includes('\\\\'), name + '에 이스케이프가 있다 - 이 테스트의 읽기 방식을 고친다');
  return new RegExp(match[1]);
}

const recording = local('recording_path_regex');
const sessionCreate = local('session_create_path_regex');
const feedback = local('feedback_path_regex');

/** WAF의 URL_DECODE에 해당한다. NORMALIZE_PATH는 테스트 입력을 정규화된 형태로만 주는 것으로 대신한다. */
function decoded(path) {
  return decodeURIComponent(path);
}

test('음성 업로드 두 경로는 예외와 집계를 받는다', () => {
  assert.match('/v0/sessions/s_1/voice-items/v3/recording', recording);
  assert.match('/v0/learning/intonation-cards/in-01/recordings', recording);
});

test('세그먼트마다 matrix 파라미터가 붙어도 매치한다 - Spring은 떼고 라우팅하므로 집계만 비껴갈 수 없어야 한다', () => {
  assert.match('/v0;a=1/sessions;b=2/s_1;c=3/voice-items;d=4/v3;e=5/recording;f=6', recording);
  assert.match('/v0/learning/intonation-cards/in-01/recordings;x=1', recording);
  assert.match('/v0;x=1/learning;y=2/intonation-cards;z=3/in-01;w=4/recordings', recording);
});

test('퍼센트 인코딩은 URL_DECODE 뒤에 매치한다', () => {
  assert.match(decoded('/v0/sessions/s_1/voice-items/v3/%72ecording'), recording);
  assert.match(decoded('/v0/learning/intonation-cards/in-01/%72ecordings'), recording);
});

test('끝 세그먼트가 경로 변수인 경로에 recordings를 넣어도 예외를 받지 않는다', () => {
  // 전부 실제 컨트롤러 경로의 마지막 세그먼트가 {변수}인 곳이다. 접미사만 보던 정규식은 여기에 걸렸다.
  for (const path of [
    '/v0/tests/recordings',
    '/v0/learning/word-sets/recordings',
    '/v0/learning/intonation-courses/recordings',
    '/v0/learning/intonation-attempts/recordings',
    '/v0/sessions/s_1/analyses/recordings',
    '/v0/sessions/s_1/analyses/recording',
  ]) {
    assert.doesNotMatch(path, recording, path);
  }
});

test('업로드 경로의 변형은 매치하지 않는다', () => {
  for (const path of [
    '/v0/learning/intonation-cards/recordings', // cardId 없음
    '/v0/learning/intonation-cards/in-01/recordings-extra',
    '/v0/learning/intonation-cards/in-01/recordings/',
    '/v0/learning/intonation-cards/in-01/x/recordings',
    '/v0/sessions/s_1/voice-items/recording', // itemId 없음
    '/v0/sessions/s_1/voice-items/v3/recordings', // 레벨테스트는 단수
    '/admin/v0/learning/intonation-cards/in-01/recordings',
    '/v1/learning/intonation-cards/in-01/recordings',
    '/v0/sessions',
    '/v0/sessions/s_1/feedback',
  ]) {
    assert.doesNotMatch(path, recording, path);
  }
});

test('세션 생성과 후기 정규식은 자기 경로만 받는다', () => {
  assert.match('/v0/sessions', sessionCreate);
  assert.match('/v0;a=1/sessions;b=2', sessionCreate);
  assert.doesNotMatch('/v0/sessions/s_1', sessionCreate);
  assert.doesNotMatch('/v0/sessions/', sessionCreate);
  assert.match('/v0/sessions/s_1/feedback', feedback);
  assert.doesNotMatch('/v0/sessions/s_1/feedback/x', feedback);
  assert.doesNotMatch('/v0/sessions/feedback', feedback);
});

test('rate 규칙과 본문 규칙 예외가 같은 recording_path_regex를 쓴다', () => {
  // 한쪽만 고치면 집계와 예외가 어긋난다. local 참조 횟수로 고정한다 - 10번 규칙 1곳 + 21번 규칙 1곳.
  const uses = source.match(/regex_string\s*=\s*local\.recording_path_regex/g) || [];
  assert.equal(uses.length, 2);
});
