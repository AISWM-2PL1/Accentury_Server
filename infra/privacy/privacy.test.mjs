// 개인정보처리방침 본문의 계약 테스트 (KAN-176 1단계).
//
// 이 파일이 있는 이유는 두 가지다.
//
// 하나는 페이지가 홀로 서야 한다는 조건이다 (KAN-133). 본문 교체는 S3에 이 파일 하나를
// 올리는 것으로 끝나야 하므로, 외부 CSS·글꼴·스크립트가 한 줄이라도 섞이면 web/ 번들이
// 바뀔 때 정책 문서가 조용히 깨진다. 심사관이 보는 페이지라 깨져도 우리가 먼저 알기 어렵다.
//
// 다른 하나는 문서가 코드보다 오래 산다는 점이다. "분석이 끝나면 즉시 삭제한다", "24시간 뒤
// 지운다" 같은 문장은 우리가 코드로 지키고 있는 약속이고, 그 코드가 바뀌면 문서가 먼저 거짓말이
// 된다. 핵심 사실을 여기서 붙들어 두면, 사실이 바뀔 때 이 테스트가 먼저 깨져서 문서를 고치라고
// 알려 준다 - 거짓 고지가 배포되는 쪽보다 낫다.
//
// 광고 사업자는 2026-09-11에 Google AdMob으로 확정됐다 (KAN-196 1단계). 그 전까지 2항(제3자
// 제공 표), 4항(국외 이전), 10항(광고)에 남아 있던 「확정 후 기재」 자리표시자를 이때 함께
// 채웠고, 이제는 되살아나는 쪽을 테스트로 막는다. 확정 전에 막지 않았던 것은 막으면 확정 전
// 단계의 본문을 커밋할 수 없어서 오히려 문서가 코드보다 뒤처졌기 때문이다.
//
// 브라우저 웹의 광고 사업자는 2026-09-13에 Google AdSense로 확정됐다 (KAN-197 1단계). AdMob이
// 웹을 지원하지 않아 사업자가 앱·웹으로 갈렸고, 그에 따라 수집 항목도 갈렸다 - 앱은 기기 광고
// 식별자, 웹은 브라우저 쿠키. 그래서 8항(자동 수집 장치)이 광고 몫을 함께 지게 됐다.
//
// `node --test 'infra/privacy/*.test.mjs'` 로 돈다. 디렉터리 경로를 그냥 넘기면 안 된다 -
// node 22.6부터 --test의 위치 인자는 glob 패턴으로 해석돼서, 디렉터리를 주면 그 이름의 모듈을
// 찾다가 MODULE_NOT_FOUND로 죽는다.
// CI는 node 22, 로컬은 26이라 양쪽에서 도는 표준 API만 쓴다.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(HERE, 'privacy.html'), 'utf8');

test('자리표시자를 막던 noindex가 없다 (KAN-133 -> KAN-176)', () => {
  // 자리표시자가 검색에 잡히지 않게 걸어 둔 줄이다. 확정 본문에 남아 있으면 스토어와 이용자가
  // 검색으로 이 문서를 찾지 못한다.
  assert.ok(!/noindex/.test(html), 'noindex 메타가 남아 있다');
});

test('외부 자원을 하나도 쓰지 않는다 (KAN-133 "S3 업로드만으로 교체")', () => {
  // 검사 목록 - 페이지가 바깥에서 무언가를 받아오는 경로 전부다.
  //   1. <script>
  //   2. 외부 스타일시트·글꼴(<link>)
  //   3. CSS @import
  //   4. CSS가 끌어오는 원격 자산 - `url(https://…)`와 프로토콜 상대 `url(//…)` 둘 다
  //   5. 자산을 끌어오는 태그 - <img> <iframe> <source> <object> <embed> <video> <audio>
  //   6. src·srcset·poster 속성 - 5번 목록이 놓친 경로를 덮는다
  // <a href>는 링크일 뿐 페이지가 무언가를 받아오지 않으므로 대상이 아니다. 본문에 외부
  // https 링크와 mailto:가 있어서, href를 6번에 넣으면 전부 오탐이 된다.
  assert.ok(!/<script/i.test(html), '<script>가 있다');
  assert.ok(!/<link/i.test(html), '<link>가 있다');
  assert.ok(!/@import/i.test(html), 'CSS @import가 있다');
  // 프로토콜 상대 경로(`url(//cdn.example/x.woff2)`)도 원격이다 - 페이지가 https로 열리면
  // 브라우저가 https로 받아온다. `url(http`만 보던 앞 판은 이 형태를 통째로 놓쳤다.
  assert.ok(!/url\(\s*['"]?\s*(?:https?:)?\/\//i.test(html), 'CSS가 원격 자산을 참조한다');
  // 자산을 끌어오는 태그 자체를 막는다. 위 넷은 스타일과 스크립트만 보므로, 본문에 이미지 한 장이
  // 끼어들면 전부 통과한다 - 그러면 S3에 html 하나를 올리는 것으로 교체가 끝나지 않는다.
  for (const tag of ['<img', '<iframe', '<source', '<object', '<embed', '<video', '<audio']) {
    assert.ok(!html.toLowerCase().includes(tag), `${tag}>가 있다`);
  }
  // 위 태그 목록이 놓친 경로(<input src>, 미래의 새 태그)까지 한 번에 덮는다. `\ssrc=`만 보던
  // 앞 판은 등호 앞 공백(`src = "…"`)과 srcset·poster를 놓쳤다 - 셋 다 유효한 HTML이다.
  assert.ok(!/\b(?:src|srcset|poster)\s*=/i.test(html), 'src·srcset·poster 속성이 있다');
});

test('자리표시자 문구가 남아 있지 않다', () => {
  assert.ok(!html.includes('이 문서는 준비 중입니다'), '자리표시자 문구가 남아 있다');
});

test('법정 필수 절이 모두 있다', () => {
  // 「개인정보 보호법」 제30조가 처리방침에 담으라고 정한 항목들과, 이 서비스에서 실제로
  // 일어나는 처리(국외 이전, 자동 수집 장치)를 함께 본다. 절을 지우거나 제목을 갈아엎으면
  // 여기서 걸린다.
  for (const heading of [
    '개인정보의 처리 목적, 처리 항목, 보유 기간',
    '개인정보의 제3자 제공',
    '개인정보 처리의 위탁',
    '개인정보의 국외 이전',
    '개인정보의 파기',
    '정보주체의 권리와 행사 방법',
    '만 14세 미만 아동의 개인정보',
    '개인정보 자동 수집 장치의 설치·운영과 거부',
    '광고',
    '개인정보의 안전성 확보 조치',
    '개인정보 보호책임자와 문의처',
    '시행일',
  ]) {
    assert.ok(html.includes(heading), `필수 절이 없다: ${heading}`);
  }
});

test('연락처가 있다 (Play·App Store 심사가 요구하는 항목)', () => {
  assert.ok(html.includes('team2pl1@gmail.com'), '보호책임자 연락처가 없다');
});

// ---------------------------------------------------------------------------
// 코드가 지키고 있는 약속. 아래 사실이 바뀌면 이 테스트가 먼저 깨져야 한다.
// ---------------------------------------------------------------------------

test('음성은 분석 직후 즉시 삭제라고 적혀 있다 (KAN-27, ai/app/tempstore.py)', () => {
  // 분석이 성공·실패·시간 초과·취소 어느 쪽으로 끝나든 임시 파일을 지운다. 이 처리가 바뀌면
  // 권한 안내 문구("녹음은 분석 후 즉시 삭제돼요")와 스토어 설명도 함께 거짓이 된다.
  // KAN-269부터 이 문장은 채점용 임시 파일의 약속이다. 선택 동의한 응시의 음성 보관은 아래 KAN-269 테스트가 본다.
  assert.ok(html.includes('즉시 삭제'), '음성 즉시 삭제 문구가 없다');
});

test('임시 파일 청소 기준 30분이 적혀 있다 (KAN-27, ai temp-retention: 30m)', () => {
  // 세션 토큰 수명(30m, backend application.yml)도 같은 값이라 문서에서 두 자리에 쓰인다.
  assert.ok(html.includes('30분'), '30분 기준이 없다');
});

test('세션·결과 보유 기간 24시간이 적혀 있다 (KAN-25, backend retention: 24h)', () => {
  assert.ok(html.includes('24시간'), '24시간 보유 기간이 없다');
});

// ---------------------------------------------------------------------------
// 1항 표의 행 단위 검사.
//
// 위 세 테스트는 「30분」·「24시간」·「즉시 삭제」가 문서 어딘가에 한 번이라도 있으면 통과한다.
// 그래서 표의 보유 기간 칸이 통째로 틀려도, 다른 절에 같은 낱말이 남아 있는 한 아무 경보가
// 울리지 않는다 (2026-09-07 Codex 검증 지적). 심사관과 이용자가 실제로 읽는 것은 이 표이므로,
// 「어느 구분의 보유 기간이 무엇인가」를 행에 묶어서 본다.
// ---------------------------------------------------------------------------

/** 1항 표의 각 행을 `{ 구분, 보유기간 }`으로 뽑는다. 의존성 없이 문자열만 자른다. */
function retentionRows() {
  const section = html.slice(
    html.indexOf('<h2>1. 개인정보의 처리 목적'),
    html.indexOf('<h2>2. 개인정보의 제3자 제공'),
  );
  assert.ok(section.length > 0, '1항을 찾지 못했다 - 절 제목이 바뀌었나');
  const rows = new Map();
  for (const row of section.split('<tr>').slice(1)) {
    const cells = [...row.matchAll(/<td>([\s\S]*?)<\/td>/g)].map((m) =>
      m[1].replace(/\s+/g, ' ').trim(),
    );
    // thead의 <th> 행은 <td>가 없어 자연히 걸러진다.
    if (cells.length < 2) continue;
    rows.set(cells[0], cells[cells.length - 1]);
  }
  return rows;
}

test('1항 표의 보유 기간이 행마다 코드와 맞는다', () => {
  const rows = retentionRows();
  // [구분, 그 행의 보유 기간 칸에 반드시 있어야 하는 문자열들, 근거]
  const expected = [
    ['음성 녹음', ['즉시 삭제'], 'KAN-27, ai/app/tempstore.py'],
    // 선택 동의한 응시의 음성은 음성 전용 버킷에 남고 수명주기 만료가 없다 (KAN-269). 이 칸이 기간으로
    // 바뀌면 버킷에 만료 규칙이 생겼다는 뜻이어야 한다.
    ['음성 저장과 학습 활용 (선택 동의)', ['학습 목적 달성 시까지'], 'KAN-269, 음성 전용 버킷(수명주기 만료 없음)'],
    // 선택 동의하지 않은 세션은 음성 없이 라벨 JSON만 같은 버킷의 _no-audio 접두에 남는다 (KAN-274, 계정 세션은
    // KAN-276부터). 버킷에 만료 규칙이 없으므로 보유 기간이 음성과 같다.
    ['분석 정보 (선택 동의하지 않은 응시)', ['학습 목적 달성 시까지'],
      'KAN-274, training/S3TrainingSampleStore.java의 saveLabelOnly'],
    // 단어 답안의 정오는 동의와 상관없이 같은 _no-audio 트리에 남는다 (KAN-276). DB의 어휘 답안(24시간)과 별개다.
    ['단어 답안 기록', ['측정 목적 달성 시까지'], 'KAN-276, training/S3VocabAnswerSampleStore.java'],
    // 미완주 세션은 생성 30분 뒤 만료 정리(SessionService.java:133), 완주 세션은 완료 시점부터
    // 24시간(TestSession.java:140-157, CompletionService.java:169-175). 기준점이 둘이라 셋을 함께 본다.
    // 앱 세션이 계정에 붙으면서(KAN-223) 행 이름에서 「익명」을 뺐다 (KAN-240).
    ['테스트 세션', ['30분', '완료', '24시간'], 'session/SessionService.java, TestSession.java'],
    // 계정은 탈퇴까지 (KAN-223 V2__app_user.sql, 탈퇴는 KAN-241). 정리 잡이 없으므로 기간이 아니라 사건이 기준이다.
    ['계정 (앱)', ['탈퇴'], 'auth/AppUser.java, V2__app_user.sql'],
    // Refresh 토큰 수명 (application.yml auth.refresh-token-ttl: 30d, RefreshTokens의 Redis TTL).
    ['로그인 토큰', ['30일', '로그아웃'], 'application.yml auth.refresh-token-ttl, auth/RefreshTokens.java'],
    ['테스트 결과와 어휘 답안', ['24시간'], 'application.yml analysis.retention: 24h'],
    // 카카오 공유 웹훅의 중복 판별용 수신 기록 (KAN-164). 기본값 7일은
    // AccenturyProperties.Share.receiptRetention의 @DefaultValue이고, 정리 잡이 그 값으로 지운다.
    ['공유 전송 알림 기록', ['7일'], 'AccenturyProperties.java:249, ShareWebhookReceiptRetention.java:36-40'],
    ['서버 운영 로그', ['14일'], 'infra/modules/fargate/variables.tf log_retention_days'],
    ['보안 로그', ['7일'], 'infra/modules/waf/variables.tf'],
    ['비정상 종료 로그', ['90일'], 'Crashlytics 콘솔 기본값'],
    // 후기는 세션·결과의 24시간과 **독립**이다 (session_feedback에 FK가 없다). 표의 이 칸이
    // 24시간 쪽으로 끌려가면 실제보다 짧은 보유 기간을 고지하게 되므로 행에 묶어 본다.
    ['이용 후기', ['1년'], 'application.yml feedback.retention: 365d, FeedbackRetention.java'],
  ];
  for (const [label, needles, source] of expected) {
    const cell = rows.get(label);
    assert.ok(cell !== undefined, `1항 표에 「${label}」 행이 없다`);
    for (const needle of needles) {
      assert.ok(
        cell.includes(needle),
        `「${label}」 행의 보유 기간에 「${needle}」이 없다 (근거: ${source}) - 실제: ${cell}`,
      );
    }
  }
});

test('세션 보유 기간의 기준점이 「생성 시점」으로 되돌아가지 않았다', () => {
  // 완주 세션의 24시간은 세션 생성이 아니라 완료 시점부터 센다 (CompletionService.java:169-175가
  // markCompleted로 만료를 다시 잡는다). 1단계 초안에 있던 「세션 생성 24시간 후」가 되살아나면
  // 실제보다 긴 보유 기간을 고지하게 된다.
  assert.ok(!html.includes('세션 생성 24시간'), '「세션 생성 24시간」이 남아 있다');
});

test('맞춤형 광고 절이 있고 동의·거부 방법이 적혀 있다 (2026-09-07 팀 결정: 1차 배포에 광고 포함)', () => {
  // 「광고」는 본문 어디에나 나오는 낱말이라 필수 절 목록의 includes만으로는 절이 실제로 있는지
  // 알 수 없다. 제목 자체를 본다.
  assert.ok(/<h2>\d+\. 광고<\/h2>/.test(html), '광고 절 제목이 없다');
  assert.ok(html.includes('맞춤형 광고'), '맞춤형 광고 고지가 없다');
  // 동의를 받는 경로(iOS ATT)와 거부하는 경로(Android 광고 개인 최적화)를 둘 다 적어야
  // 이용자가 실제로 철회할 수 있다. 스토어 심사도 이 두 가지를 본다.
  assert.ok(html.includes('앱 추적 투명성'), 'iOS ATT 안내가 없다');
  assert.ok(html.includes('광고 개인 최적화'), 'Android 광고 개인 최적화 해제 안내가 없다');
});

test('비맞춤 광고에서 「광고 식별자 대신」이라고 적지 않는다 (KAN-196 리뷰 P1-4, npa의 실제 동작)', () => {
  // 1단계 초안은 「동의하지 않으시면 … 광고 식별자 대신 IP 주소와 기기 정보 같은 최소한의 정보가
  // 쓰일 수 있습니다」였다. npa=1은 식별자 전송을 막는 플래그가 아니다 — Google은 비맞춤 광고에서도
  // 식별자를 빈도 제한·집계 보고·부정 방지에 쓴다(support.google.com/admob/answer/7676680). 「대신」은
  // 식별자를 안 쓴다는 말이라 거짓 고지가 되므로, 본문을 손보다가 되살아나는 것을 여기서 막는다.
  const ads = section('<h2>10. 광고</h2>', '<h2>11. 개인정보의 안전성 확보 조치');
  assert.ok(!ads.includes('광고 식별자 대신'), '비맞춤 광고가 식별자를 안 쓴다는 문장이 남아 있다');
  assert.ok(ads.includes('빈도'), '비맞춤 광고에서 식별자를 쓰는 용도(빈도 제한)가 적혀 있지 않다');
  assert.ok(ads.includes('부정 사용 방지'), '비맞춤 광고에서 식별자를 쓰는 용도(부정 사용 방지)가 적혀 있지 않다');
});

test('"광고와 추적이 없습니다"가 남아 있지 않다 (광고 도입으로 거짓이 된 문장)', () => {
  // KAN-176 1단계 초안에 있던 문장이다. 1차 배포에 맞춤형 광고를 넣기로 한 2026-09-07 팀 결정
  // 이후로는 거짓 고지라서, 본문을 손보다가 되살아나는 것을 여기서 막는다.
  assert.ok(!html.includes('광고와 추적이 없습니다'), '거짓이 된 "광고와 추적이 없습니다"가 남아 있다');
});

test('진행 기록의 삭제 시점이 적혀 있다 (KAN-198, web/src/App.tsx)', () => {
  // 「서비스가 따로 지우지 않으므로」는 clearSnapshot 호출점이 하나도 없던 시절의 사실이다.
  // 결과 화면 진입(clearSnapshot)과 인트로 진입(sweepSnapshots)에 삭제를 결선한 뒤로는 거짓
  // 고지라서, 본문을 손보다가 되살아나는 것을 여기서 막는다. 반대 방향(배선이 사라지는 쪽)은
  // web/src/App.test.tsx가 먼저 깨져서 알려 준다.
  assert.ok(!html.includes('서비스가 따로 지우지 않'), '삭제 배선 전의 문장이 남아 있다');
  assert.ok(html.includes('결과 화면을 보시는 때에'), '진행 기록을 지우는 시점이 적혀 있지 않다');
  assert.ok(html.includes('다음에 첫 화면을 여시면'), '끊긴 응시의 기록을 지우는 시점이 없다');
});

test('「확정 후 기재」 자리표시자가 남아 있지 않다 (KAN-196)', () => {
  // 2026-09-11 사업자 확정으로 2·4·10항의 자리표시자를 전부 채웠다. 본문을 손보다가 한 자리라도
  // 되살아나면 prod에 미정 문구가 게시되므로 여기서 막는다. 10항의 동의 설정 위치에 있던
  // 「도입 시 위치 확정」도 같은 자리표시자다.
  assert.ok(!html.includes('확정 후 기재'), '「확정 후 기재」가 남아 있다');
  assert.ok(!html.includes('도입 시 위치 확정'), '「도입 시 위치 확정」이 남아 있다');
});

/** 절 제목 사이의 본문을 자른다. `from` 제목부터 `to` 제목 직전까지. */
function section(from, to) {
  const start = html.indexOf(from);
  const end = html.indexOf(to);
  assert.ok(start >= 0 && end > start, `절을 찾지 못했다: ${from} ~ ${to}`);
  return html.slice(start, end);
}

test('2항 제3자 제공 표와 10항 광고 절에 Google AdMob이 적혀 있다 (KAN-196 2026-09-11 사업자 확정)', () => {
  // 「Google」은 3항·4항의 Firebase 행에도 나오므로 문서 전체 includes로는 광고 사업자가 적혔는지
  // 알 수 없다. 광고 사업자를 적어야 하는 절 둘을 각각 본다.
  const thirdParty = section('<h2>2. 개인정보의 제3자 제공', '<h2>3. 개인정보 처리의 위탁');
  // 정확 일치로 보지 않는다 - KAN-197에서 웹 사업자 Google AdSense를 같은 행에 합쳐
  // 「Google LLC (Google AdMob, Google AdSense)」가 됐다. 별도 행을 만들지 않은 이유는 4항과
  // 같다: AdMob도 AdSense도 Google LLC라, 행을 나누면 같은 사업자를 두 번 고지하게 된다.
  assert.ok(thirdParty.includes('Google AdMob'), '2항 제3자 제공 표에 Google AdMob이 없다');
  const ads = section('<h2>10. 광고</h2>', '<h2>11. 개인정보의 안전성 확보 조치');
  assert.ok(ads.includes('Google AdMob'), '10항 광고 절에 Google AdMob이 없다');
  // 사업자가 정해졌으니 광고가 어디서 나오는지도 적어야 한다 (전면 광고는 분석 대기, 보상형은 재응시).
  assert.ok(ads.includes('전면 광고'), '10항에 전면 광고 자리가 없다');
  assert.ok(ads.includes('보상형 광고'), '10항에 보상형 광고 자리가 없다');
});

test('4항 국외 이전에 광고 항목이 들어 있다 (KAN-196, Google LLC 행에 합침)', () => {
  // AdMob도 Google LLC라 Firebase·GA와 같은 이전받는 자다. 별도 행을 만들지 않고 기존 Google LLC
  // 행의 이전받는 자·이전되는 항목·이용 목적에 광고 몫을 더했다. 셋 중 하나라도 빠지면 광고
  // 식별자가 국외로 가는 사실을 고지하지 않는 셈이다.
  const abroad = section('<h2>4. 개인정보의 국외 이전', '<h3>이용 통계 이벤트');
  assert.ok(abroad.includes('Google AdMob'), '4항 이전받는 자에 Google AdMob이 없다');
  assert.ok(abroad.includes('광고 식별자'), '4항 이전되는 항목에 광고 식별자가 없다');
  assert.ok(abroad.includes('맞춤형 광고 표시'), '4항 이용 목적에 맞춤형 광고 표시가 없다');
});

test('브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다 (KAN-197 2026-09-13 웹 사업자 확정)', () => {
  // 앱은 AdMob, 브라우저 웹은 AdSense다 - AdMob은 웹을 지원하지 않는다. 사업자가 갈리면서 수집
  // 항목도 갈렸고(앱=기기 광고 식별자, 웹=브라우저 쿠키), 그래서 웹 몫을 적어야 하는 절이 넷이다.
  // 하나라도 빠지면 브라우저로 오신 분에게는 고지가 아닌 문서가 된다.
  const thirdParty = section('<h2>2. 개인정보의 제3자 제공', '<h2>3. 개인정보 처리의 위탁');
  assert.ok(thirdParty.includes('Google AdSense'), '2항 제3자 제공 표에 Google AdSense가 없다');

  const abroad = section('<h2>4. 개인정보의 국외 이전', '<h3>이용 통계 이벤트');
  assert.ok(abroad.includes('Google AdSense'), '4항 이전받는 자에 Google AdSense가 없다');
  assert.ok(abroad.includes('광고 쿠키'), '4항 이전되는 항목에 광고 쿠키가 없다');

  // 8항은 자동 수집 장치(쿠키) 절이다. 웹 광고 태그가 쿠키를 심는 것은 GA4 태그와 같은 종류의
  // 사실이라 같은 자리에서 고지해야 한다 - 10항에만 적으면 「쿠키를 심는 장치」를 묻는 절이 웹
  // 광고를 빠뜨린 채로 남는다.
  const autoCollect = section('<h2>8. 개인정보 자동 수집 장치', '<h2>9.');
  assert.ok(autoCollect.includes('AdSense'), '8항에 AdSense 광고 태그 고지가 없다');
  assert.ok(autoCollect.includes('쿠키'), '8항에 광고 쿠키 고지가 없다');

  const ads = section('<h2>10. 광고</h2>', '<h2>11. 개인정보의 안전성 확보 조치');
  assert.ok(ads.includes('Google AdSense'), '10항 광고 절에 Google AdSense가 없다');
  assert.ok(ads.includes('배너 광고'), '10항에 웹 배너 광고 자리가 없다');
  // 웹에는 보상형(재응시) 광고가 없다. 한 절이 앱·웹을 함께 말하므로, 이 문장이 빠지면 앞의
  // 「보상형 광고를 끝까지 보신 뒤에 재응시」가 웹에도 걸리는 것처럼 읽힌다 - 실제로는 웹의
  // 재응시는 광고 없이 통과한다. 이 문장만 공백을 눌러서 보는 이유는 본문이 80자에서 접혀
  // 낱말 사이에 줄바꿈과 들여쓰기가 들어가기 때문이다 (1항 표의 `retentionRows`와 같은 처리).
  const adsFlat = ads.replace(/\s+/g, ' ');
  assert.ok(adsFlat.includes('웹의 재응시에는 광고가 없'), '10항에 웹 재응시 광고 없음이 적혀 있지 않다');
  // 웹의 철회 경로. 앱의 OS 설정(Android 광고 ID 재설정·iOS ATT)에 대응하는 자리다.
  assert.ok(ads.includes('adssettings.google.com'), '10항에 Google 광고 설정 링크가 없다');
});

test('이용 후기 절이 있고 이메일이 선택·회신 전용이라고 적혀 있다 (KAN-211)', () => {
  // 이 서비스가 받는 **유일한 개인 식별 정보**가 후기의 회신 이메일이다 (2026-09-15 결정).
  // 그래서 이 절 하나가 아니라 네 자리가 함께 서야 고지가 성립한다 — 무엇을 받는지(1항 아래
  // 산문), 언제 지우는지(5항), 어디로 나가는지(4항), 지워 달라고 어떻게 말하는지(6항).
  // 「이용 후기」는 1항 표에도 나오는 낱말이라 절 제목 자체를 본다.
  assert.ok(html.includes('<h3>이용 후기</h3>'), '1항 아래 「이용 후기」 절이 없다');

  const feedback = section('<h3>이용 후기</h3>', '<h3>접속 정보와 서버 로그</h3>');
  // 이메일이 **선택**이라는 것과 **회신에만 쓴다**는 것 둘이 화면 문구
  // (`web/src/feedback/feedbackText.ts`의 FEEDBACK_EMAIL_HINT)와 같은 범위를 말해야 한다.
  // 한쪽만 남으면 "필수인가"나 "다른 데도 쓰나"가 열린 채로 남는다.
  assert.ok(feedback.includes('선택'), '이용 후기 절에 이메일이 선택 항목이라는 말이 없다');
  assert.ok(
    feedback.includes('회신') || feedback.includes('답변'),
    '이용 후기 절에 이메일을 회신·답변에 쓴다는 말이 없다',
  );

  // 5항 파기. 1항 표의 「1년」과 짝이고, 파기 절에 없으면 "언제 지우는가"를 묻는 절이 후기를
  // 빠뜨린 채로 남는다.
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리');
  assert.ok(disposal.includes('1년'), '5항 파기 목록에 후기의 1년이 없다');

  // 6항 권리. 계정이 없는 서비스에서 이용자를 지목할 수 있는 유일한 경로라, 삭제 요청을 어디로
  // 보내는지(13항 문의처)까지 적혀 있어야 실제로 행사할 수 있는 권리가 된다.
  const rights = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세 미만');
  assert.ok(rights.includes('삭제'), '6항에 후기 삭제 요청 경로가 없다');
  assert.ok(rights.includes('13항'), '6항에 삭제 요청을 보낼 문의처(13항)가 없다');

  // 4항 국외 이전. 후기 본문은 이용자가 자유 서술한 내용이라 개인정보가 섞일 수 있고, 그것이
  // 미국 사업자의 메신저로 나간다 (FeedbackSlackNotifier).
  const abroad = section('<h2>4. 개인정보의 국외 이전', '<h3>이용 통계 이벤트');
  assert.ok(abroad.includes('Slack'), '4항 국외 이전에 Slack 행이 없다');
});

test('후기 이메일이 내부 알림에 실리지 않는다고 적혀 있다 (KAN-211, FeedbackSlackNotifier)', () => {
  // `FeedbackSlackNotifier.message`가 싣는 것은 연락처의 **유무**뿐이다 — 채널은 개발팀 전원이
  // 보고 슬랙 무료 플랜은 지난 메시지를 지우지 않아, 한 번 흘리면 되돌릴 방법이 없다. 그 약속이
  // 4항의 「이메일 주소는 이전하지 않습니다」를 떠받치므로, 코드가 바뀌면 여기가 먼저 깨져야 한다.
  assert.ok(html.includes('이메일 주소를 싣지 않'), '내부 알림에 이메일을 싣지 않는다는 문구가 없다');
  // 로그 쪽 약속은 11항이 맡는다 (`LogMasking`의 EMAIL이 마지막 관문이다).
  const safety = section('<h2>11. 개인정보의 안전성 확보 조치', '<h2>12. 동의를 받는 방식');
  assert.ok(safety.includes('로그와 내부 알림'), '11항에 후기 이메일의 로그·알림 비적재가 없다');
});

test('데이터 소재지가 서울 리전이라고 적혀 있다 (infra, AWS ap-northeast-2)', () => {
  // 국외 이전 고지의 반대편이다. 음성·세션·결과는 국내에 머무르고 Google로 가는 것은
  // 이용 통계와 오류 로그뿐이라는 구분이 이 문구에 걸려 있다.
  // 선택 동의로 보관하는 음성의 버킷도 같은 리전이다 (KAN-269).
  assert.ok(html.includes('ap-northeast-2'), '리전 표기가 없다');
  assert.ok(html.includes('서울 리전'), '서울 리전 표기가 없다');
});

// ── 환경별 본문 (KAN-239 -> KAN-269) ──────────────────────────────────────────
// KAN-239는 staging에만 학습 수집 고지를 붙였다. KAN-269부터 음성 저장은 두 환경 공통의 선택 동의라
// staging 전용 고지가 사라졌고, staging-only 블록은 비어 있다. 자르는 규칙은 게시 스크립트 하나에
// 그대로 있고(--render), 여기서는 그 스크립트를 그대로 돌려 두 환경에 올라갈 본문을 검사한다.
const SCRIPT = join(HERE, '..', '..', 'scripts', 'publish-privacy.sh');
const rendered = (env) => execFileSync('bash', [SCRIPT, '--render', env], { encoding: 'utf8' });
/** 표식 줄을 뺀 본문. 두 환경의 고지가 같은지 대조할 때 쓴다. */
const withoutMarkers = (body) => body.split('\n').filter((line) => !line.includes('staging-only')).join('\n');

test('staging-only 표식은 짝이 맞고 각자 한 줄을 차지한다 (KAN-239)', () => {
  // 스크립트는 줄 단위로 자른다. 표식이 다른 내용과 한 줄에 있거나 짝이 안 맞으면 prod 본문의
  // 뒷부분이 통째로 잘리거나 고지가 prod에 샌다. 블록이 비어 있어도 표식은 남겨 자르는 경로를 계속 돌린다.
  const lines = html.split('\n');
  const begins = lines.filter((line) => line.includes('staging-only:begin'));
  const ends = lines.filter((line) => line.includes('staging-only:end'));
  assert.equal(begins.length, ends.length, '표식의 짝이 맞지 않는다');
  assert.ok(begins.length >= 1, 'staging-only 블록이 없다');
  for (const line of [...begins, ...ends]) {
    assert.match(line, /^<!-- staging-only:(begin|end) -->$/, `표식이 한 줄을 통째로 차지하지 않는다: ${line}`);
  }
});

test('prod 본문은 표식 없이 끝까지 게시되고 옛 staging 고지가 없다 (KAN-239 -> KAN-269)', () => {
  const prod = rendered('prod');
  assert.ok(!prod.includes('staging-only'), 'prod 본문에 표식이 남았다');
  assert.ok(!prod.includes('내부 테스트 환경'), 'prod 본문에 옛 staging 고지가 새었다');
  assert.ok(prod.includes('</html>'), 'prod 본문 뒷부분이 잘렸다');
});

test('두 환경의 게시 본문이 같다 - 음성 고지에 환경별 차이가 없다 (KAN-269)', () => {
  // 음성 저장은 두 환경이 같은 선택 동의로 한다. staging에만 붙는 고지가 다시 생기면 prod와 다른
  // 약속을 하게 되므로, 블록을 다시 채울 때는 이 테스트를 고치면서 그 이유를 적는다.
  const staging = rendered('staging');
  assert.equal(withoutMarkers(staging), rendered('prod'), 'staging 본문이 prod 본문과 다르다');
  for (const stale of ['내부 테스트 환경', '동의한 테스터 계정', '가명 값', '보유 기간이 끝나는 날']) {
    assert.ok(!staging.includes(stale), `KAN-239 시절의 staging 고지가 남아 있다: ${stale}`);
  }
});

// ── 음성 저장과 학습 활용의 선택 동의 (KAN-269) ────────────────────────────────
// 선택 동의한 응시의 음성은 두 환경 모두 음성 전용 S3 버킷에 남는다. 「음성은 저장하지 않는다」가 조건 없이
// 남으면 거짓 고지이고, 반대로 선택 동의 절이 빠지면 수집을 고지하지 않은 것이 된다. 양쪽을 붙든다.

/** 본문에서 선택 동의 절만 자른 것. 줄바꿈으로 접힌 문장을 대조하려고 공백을 하나로 누른다. */
function voiceConsentSection(body) {
  const from = body.indexOf('<h3>음성 저장과 AI 모델 학습 활용 (선택 동의)</h3>');
  const to = body.indexOf('<h3>선택 동의하지 않으신 경우에 남기는 분석 정보</h3>');
  assert.ok(from >= 0 && to > from, '선택 동의 절을 찾지 못했다');
  return body.slice(from, to).replace(/\s+/g, ' ');
}

test('prod 본문에 음성 저장 선택 동의 절이 있고 대상, 항목, 장소, 기간, 철회를 적는다 (KAN-269)', () => {
  const consent = voiceConsentSection(rendered('prod'));
  // 선택 동의이고 거부해도 불이익이 없다.
  assert.ok(consent.includes('선택 동의'), '선택 동의라는 말이 없다');
  assert.ok(consent.includes('필수 동의와 별개'), '필수 동의와 별개라는 말이 없다');
  assert.ok(consent.includes('서비스 이용에는 아무런 제한이 없습니다'), '거부해도 제한이 없다는 말이 없다');
  // 대상은 웹과 앱 모두이고, 웹에는 다른 나이 확인이 없어 동의 문안이 만 14세 이상 확인을 겸한다.
  assert.ok(consent.includes('브라우저 웹과 앱'), '대상(웹과 앱)이 없다');
  assert.ok(consent.includes('만 14세 이상임을 확인'), '만 14세 이상 확인이 없다');
  // 저장 항목. 라벨 파일의 필드가 늘면 여기와 본문을 함께 늘린다.
  for (const item of [
    'WAV', '분석 작업 식별자', '세션 식별자', '문항 식별자', '출신 지역', '식별 키', '테스트 버전', '채점 버전',
    '음성 길이', '최종 상태', '억양 원점수', '음질 판정 코드', '모델 버전', 'AI 채점 버전', '오류 코드',
    '추적용 식별자', '동의하신 문안의 버전과 동의 시각', '저장 시각',
  ]) {
    assert.ok(consent.includes(item), `선택 동의 절의 저장 항목에 「${item}」이 없다`);
  }
  // 식별자는 가명화하지 않는다 (KAN-239의 가명화를 되돌린 상태다).
  assert.ok(consent.includes('가명으로 바꾸지 않고 그대로 저장'), '식별자를 그대로 저장한다는 말이 없다');
  // 앱 계정의 연결 기록은 세션 만료와 탈퇴 뒤에도 남는다.
  assert.ok(consent.includes('연결 기록'), '계정과 음성의 연결 기록이 없다');
  assert.ok(consent.includes('탈퇴하신 뒤에도 남습니다'), '연결 기록이 탈퇴 뒤에도 남는다는 말이 없다');
  // 웹 익명 세션은 만료 뒤 개별 삭제 요청을 맞출 수 없다.
  assert.ok(consent.includes('개별 삭제 요청'), '웹 세션 만료 뒤의 개별 삭제 요청 한계가 없다');
  // 장소와 보호 조치.
  assert.ok(consent.includes('서울 리전(ap-northeast-2)'), '보관 리전이 없다');
  assert.ok(consent.includes('Amazon S3'), '보관 장소(S3)가 없다');
  assert.ok(consent.includes('버전 기록'), '버전 기록이 없다');
  assert.ok(consent.includes('암호화'), '저장 시 암호화가 없다');
  assert.ok(consent.includes('HTTPS'), 'HTTPS 전용이 없다');
  assert.ok(consent.includes('모델 학습 담당자'), '읽기 권한 제한이 없다');
  // 기간과 철회.
  assert.ok(consent.includes('학습 목적 달성 시까지'), '보유 기간이 없다');
  assert.ok(consent.includes('철회'), '철회 방법이 없다');
  assert.ok(consent.includes('13항'), '이미 저장된 음성의 처리 요청처(13항)가 없다');
});

test('「음성을 저장하지 않는다」는 선택 동의를 하지 않은 경우로 한정돼 있다 (KAN-269)', () => {
  // 두 환경 모두 본다. 조건 없는 미보존 문장이 한 자리라도 남으면 동의한 사람에게는 거짓 고지다.
  for (const env of ['prod', 'staging']) {
    const flat = rendered(env).replace(/\s+/g, ' ');
    assert.ok(
      flat.includes('동의하지 않으신 경우, 음성은 데이터베이스나 S3 같은 영속 저장소에 저장하지 않습니다'),
      `${env}: 미보존 문구가 선택 동의 조건에 묶여 있지 않다`,
    );
    for (const unconditional of [
      '<p> 음성은 데이터베이스나 S3 같은 영속 저장소에 저장하지 않습니다',
      '<p> 녹음한 음성은 억양을 분석해 점수를 매기는 데에만 씁니다',
      '<li>음성: 분석이 끝나는 즉시 삭제',
      '같은 내용을 말합니다. 녹음한 음성은 점수를 매기는 그 순간에만 쓰고 곧바로 지웁니다',
      '음성은 서비스 서버와 분석 서버(모두 저희가 운영합니다)의 메모리와 임시 파일로만 흐르고',
    ]) {
      assert.ok(!flat.includes(unconditional), `${env}: 조건 없는 음성 미보존 문장이 남아 있다: ${unconditional}`);
    }
  }
});

test('선택 동의가 5, 6, 7, 11, 12항에도 반영돼 있다 (KAN-269)', () => {
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리').replace(/\s+/g, ' ');
  assert.ok(disposal.includes('학습 목적 달성 시까지'), '5항에 선택 동의 음성의 보유 기간이 없다');
  assert.ok(disposal.includes('자동 만료는 두지 않습니다'), '5항에 자동 만료가 없다는 말이 없다');

  const rights = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세 미만').replace(/\s+/g, ' ');
  assert.ok(rights.includes('선택 동의는 언제든 철회하실 수 있습니다'), '6항에 선택 동의 철회가 없다');

  const children = section('<h2>7. 만 14세 미만', '<h2>8. 개인정보 자동 수집 장치').replace(/\s+/g, ' ');
  assert.ok(children.includes('선택 동의 문안에는 본인이 만 14세 이상임을 확인'), '7항에 웹의 연령 확인 방식이 없다');

  const safety = section('<h2>11. 개인정보의 안전성 확보 조치', '<h2>12. 동의를 받는 방식').replace(/\s+/g, ' ');
  assert.ok(safety.includes('선택 동의로 보관하는 음성'), '11항에 보관 음성의 보호 조치가 없다');

  const consent = section('<h2>12. 동의를 받는 방식', '<h2>13. 개인정보 보호책임자').replace(/\s+/g, ' ');
  assert.ok(consent.includes('별도의 선택 동의'), '12항에 음성 선택 동의를 받는 방식이 없다');
});

// ── 웹의 출신 지역 수집과 로그인 없는 앱 판 (KAN-274) ──────────────────────────────
// 웹의 출신 지역 선택 화면이 두 환경에 늘 서게 됐고(빌드 스위치 제거), 앱의 첫 스토어 심사 빌드는 로그인을 끈
// 익명 모드다 (App 레포 KAN-270의 LOGIN_ENABLED). 방침이 「앱은 로그인한 계정으로 이용한다」만 말하면 그
// 빌드의 이용자에게는 거짓 고지가 되므로, 익명 앱의 처리가 서야 하는 자리(머리말, 1, 6, 7, 12항)를 붙든다.
// 그리고 익명 세션은 음성 저장에 동의하지 않아도 음성 없이 분석 정보(점수와 출신 지역)를 남긴다
// (S3TrainingSampleStore의 라벨 전용 저장). 지역도 그래서 동의와 무관하게 모두에게 묻는다.

test('테스트 세션 절에 웹에서 출신 지역을 묻는다는 사실과 목적이 적혀 있다 (KAN-274)', () => {
  const session = section('<h3>테스트 세션</h3>', '<h3>계정 (앱 소셜 로그인)</h3>').replace(/\s+/g, ' ');
  // 웹과 익명 앱 모두 동의와 무관하게 묻는다 (App 레포 App.tsx의 IntroRoute, needsAnonymousRegion).
  assert.ok(
    session.includes('브라우저 웹과 로그인 없이 이용하는 앱에서는 응시를 시작하기 전에 출신 지역'),
    '웹과 익명 앱이 응시 전에 출신 지역을 묻는다는 사실이 없다',
  );
  assert.ok(session.includes('선택 동의 여부와 관계없이 여쭙니다'), '지역을 동의와 무관하게 묻는다는 말이 없다');
  assert.ok(session.includes('고르신 값을 세션에 기록합니다'), '고른 지역을 세션에 기록한다는 말이 없다');
  // 목적 문구는 계정 절의 출신 지역 항목과 같아야 한다 - 같은 값을 두 절이 다른 목적으로 말하면 안 된다.
  const purpose = '출신 지역별 응시자 구성 파악과 억양 분석 개선';
  assert.ok(session.includes(purpose), '테스트 세션 절에 출신 지역의 목적이 없다');
  const account = section('<h3>계정 (앱 소셜 로그인)</h3>', '<h3>익명 통계</h3>').replace(/\s+/g, ' ');
  assert.ok(account.includes(purpose), '계정 절의 출신 지역 목적 문구가 바뀌었다 - 두 절을 함께 맞춘다');
  // 1항 표의 테스트 세션 행도 본문과 같은 목적을 말한다.
  const table = section('<h2>1. 개인정보의 처리 목적', '<h3>음성 녹음</h3>').replace(/\s+/g, ' ');
  assert.ok(table.includes(`${purpose}(출신 지역에 한함)`), '1항 표의 테스트 세션 행 목적에 출신 지역의 목적이 없다');
});

test('로그인 없이 이용하는 앱 판의 처리가 적혀 있다 (KAN-274, App KAN-270 익명 모드)', () => {
  const flat = html.replace(/\s+/g, ' ');
  // 머리말: 앱에 두 판이 있고, 로그인 없는 판은 웹처럼 익명 세션이다.
  assert.ok(flat.includes('로그인 없이 이용하는 판과 로그인해 이용하는 판이 있습니다'), '머리말에 앱의 두 판이 없다');
  assert.ok(flat.includes('계정 없는 익명 세션 하나로 처리됩니다'), '로그인 없는 앱의 응시가 익명 세션이라는 말이 없다');
  assert.ok(flat.includes('계정에 관한 내용은 로그인해 이용하는 판에만 적용됩니다'), '계정 고지의 적용 범위가 없다');

  // 선택 동의 절: 동의를 기기에 저장하고 세션마다 기록한다 (AnonymousVoiceConsentStore), 철회는 설정 화면에서
  // 하고 다음 테스트부터 적용된다 (익명 세션의 동의는 세션 생성 때 고정된다).
  const consent = voiceConsentSection(html);
  assert.ok(consent.includes('앱을 설치한 기기에 저장'), '익명 앱의 동의를 기기에 저장한다는 말이 없다');
  assert.ok(consent.includes('세션마다 동의 사실을 기록'), '익명 앱이 세션마다 동의를 기록한다는 말이 없다');
  assert.ok(consent.includes('앱의 설정 화면에서 철회'), '익명 앱의 철회 방법이 없다');
  assert.ok(consent.includes('그 뒤에 시작하시는 테스트부터 적용'), '익명 앱의 철회가 다음 테스트부터라는 말이 없다');

  // 테스트 세션 절: 익명 앱은 지역을 한 번 묻고 기기에 저장해 다음 세션에도 싣는다 (AnonymousVoiceConsentStore.saveRegion).
  const session = section('<h3>테스트 세션</h3>', '<h3>계정 (앱 소셜 로그인)</h3>').replace(/\s+/g, ' ');
  assert.ok(session.includes('브라우저 웹과 로그인 없이 이용하는 앱의 세션은 익명'), '익명 앱의 세션이 익명이라는 말이 없다');
  assert.ok(session.includes('로그인 없이 이용하는 앱은 처음 한 번 여쭌 뒤 고르신 값을 앱을 설치한 기기에 저장'), '익명 앱이 지역을 기기에 저장한다는 말이 없다');

  // 계정 절은 로그인하는 판에만 적용된다.
  const account = section('<h3>계정 (앱 소셜 로그인)</h3>', '<h3>익명 통계</h3>').replace(/\s+/g, ' ');
  assert.ok(account.includes('이 절은 로그인해 이용하는 앱에만 적용됩니다'), '계정 절의 적용 범위가 없다');

  // 6항: 익명 앱도 세션 만료 뒤에는 개별 삭제 요청을 맞출 수 없다.
  const rights = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세 미만').replace(/\s+/g, ' ');
  assert.ok(rights.includes('브라우저 웹이나 로그인 없이 이용하는 앱에서 동의하신 경우'), '6항에 익명 앱의 개별 삭제 한계가 없다');

  // 7항: 익명 앱에는 계정이 없어 나이를 묻지 않는다.
  const children = section('<h2>7. 만 14세 미만', '<h2>8. 개인정보 자동 수집 장치').replace(/\s+/g, ' ');
  assert.ok(children.includes('브라우저 웹과 로그인 없이 이용하는 앱에는 계정이 없어 나이를 묻지 않습니다'), '7항에 익명 앱의 연령 확인 방식이 없다');

  // 12항: 익명 앱에는 가입 동의 화면이 없다.
  const method = section('<h2>12. 동의를 받는 방식', '<h2>13. 개인정보 보호책임자').replace(/\s+/g, ' ');
  assert.ok(method.includes('브라우저 웹과 로그인 없이 이용하는 앱은 아래의 선택 동의를 하지 않으시면'), '12항에 익명 앱의 동의 방식이 없다');
});

test('선택 동의하지 않은 응시의 분석 정보 보관이 적혀 있다 (KAN-274, KAN-276, S3TrainingSampleStore.saveLabelOnly)', () => {
  const flat = html.replace(/\s+/g, ' ');
  assert.ok(html.includes('<h3>선택 동의하지 않으신 경우에 남기는 분석 정보</h3>'), '분석 정보 보관 절이 없다');
  const labels = section('<h3>선택 동의하지 않으신 경우에 남기는 분석 정보</h3>', '<h3>테스트 세션</h3>').replace(/\s+/g, ' ');
  // 대상은 웹, 로그인 없는 앱, 로그인 앱 모두이고(계정 세션은 KAN-276부터) 음성은 남기지 않는다.
  assert.ok(labels.includes('브라우저 웹과 로그인 없이 이용하는 앱, 로그인해 이용하는 앱 모두'), '대상(웹과 앱 전부)이 없다');
  assert.ok(labels.includes('음성 녹음은 남기지 않으며'), '음성을 남기지 않는다는 말이 없다');
  assert.ok(labels.includes('억양 분석 AI 모델을 개선하고 출신 지역별 응시자 구성을 파악'), '목적이 없다');
  // 라벨 JSON의 필드. 필드가 늘면 여기와 본문을 함께 늘린다. 동의 버전과 동의 시각은 이 건에는 없다.
  for (const item of [
    '분석 작업 식별자', '세션 식별자', '문항 식별자', '출신 지역', '식별 키', '테스트 버전', '채점 버전',
    '음성 길이', '최종 상태', '억양 원점수', '음질 판정 코드', '모델 버전', 'AI 채점 버전', '오류 코드',
    '추적용 식별자', '저장 시각',
  ]) {
    assert.ok(labels.includes(item), `분석 정보 절의 항목에 「${item}」이 없다`);
  }
  assert.ok(!labels.includes('동의하신 문안의 버전'), '동의하지 않은 건에 동의 버전을 남긴다고 적혀 있다');
  assert.ok(labels.includes('가명으로 바꾸지 않고 그대로 저장'), '식별자를 그대로 저장한다는 말이 없다');
  assert.ok(labels.includes('개별 삭제 요청'), '세션 만료 뒤의 개별 삭제 요청 한계가 없다');
  // 한계에는 예외가 있다 (Codex 리뷰 P2). 후기에 이메일을 적어 보낸 응시는 session_feedback이 세션 식별자와
  // 이메일을 1년 동안 함께 들고 있어(SessionFeedback.java) 세션이 지워진 뒤에도 그 분석 정보를 찾을 수 있다.
  // 「어느 분의 것인지 알 수 없다」만 적으면 그 응시자에게는 거짓 고지다.
  assert.ok(labels.includes('후기에 함께 남는 세션 식별자로 해당 정보를 찾을 수 있습니다'), '후기 이메일로 이어지는 경우의 예외가 없다');
  assert.ok(labels.includes('Amazon S3'), '보관 장소가 없다');
  assert.ok(labels.includes('음성과 구분된 위치'), '음성과 구분된 위치에 둔다는 말이 없다');
  assert.ok(labels.includes('학습 목적 달성 시까지'), '보유 기간이 없다');
  // 계정 세션도 라벨을 남기되 계정 id는 싣지 않는다 (KAN-276, VoiceUploadService - 대응표도 쓰지 않는다).
  assert.ok(!labels.includes('선택 동의하지 않으시면 이 분석 정보를 남기지 않습니다'), '계정 세션 제외 문장이 남아 있다');
  assert.ok(labels.includes('계정의 식별자를 넣지 않으며'), '계정 세션의 라벨에 계정 식별자가 없다는 말이 없다');

  // 다른 자리의 문장이 이 보관과 어긋나지 않는다: 24시간 삭제의 예외, 5항 파기, 12항.
  const session = section('<h3>테스트 세션</h3>', '<h3>계정 (앱 소셜 로그인)</h3>').replace(/\s+/g, ' ');
  assert.ok(session.includes('그 절에 적은 대로 따로 보관합니다'), '24시간 삭제 문장에 분석 정보 보관의 예외가 없다');
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리').replace(/\s+/g, ' ');
  assert.ok(disposal.includes('선택 동의하지 않으신 응시(브라우저 웹, 앱)의 분석 정보'), '5항에 분석 정보의 보유가 없다');
  const method = section('<h2>12. 동의를 받는 방식', '<h2>13. 개인정보 보호책임자').replace(/\s+/g, ' ');
  assert.ok(method.includes('선택 동의를 하지 않으셔도 음성 없이 남기는 분석 정보'), '12항에 분석 정보 보관의 안내가 없다');
  // 동의한 익명 세션의 음성에도 같은 예외가 적혀 있다.
  assert.ok(
    voiceConsentSection(html).includes('후기에 함께 남는 세션 식별자로 해당 음성을 찾을 수 있습니다'),
    '선택 동의 절에 후기 이메일로 이어지는 경우의 예외가 없다',
  );
  // 이용 후기 절이 후기에 세션 식별자가 남는다는 사실을 적는다 (session_feedback.session_id).
  const feedback = section('<h3>이용 후기</h3>', '<h3>접속 정보와 서버 로그</h3>').replace(/\s+/g, ' ');
  assert.ok(feedback.includes('후기를 쓰신 세션의 식별자'), '이용 후기 절에 세션 식별자가 함께 남는다는 말이 없다');
  // 1항 표의 이용 후기 행도 같은 사실을 적는다 - 표와 본문이 갈리면 안 된다 (검증 리뷰 지적).
  const table = section('<h2>1. 개인정보의 처리 목적', '<h3>음성 녹음</h3>').replace(/\s+/g, ' ');
  assert.ok(/<td>이용 후기<\/td>.*?후기를 쓰신 세션의 식별자/.test(table), '1항 표의 이용 후기 행에 세션 식별자가 없다');
  // 6항이 「찾아 드릴 수 없다」를 예외 없이 말하지 않는다 - 후기 이메일로 이어지는 경우를 함께 적는다.
  const rights6 = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세 미만').replace(/\s+/g, ' ');
  assert.ok(rights6.includes('이용 후기에 답변 받을 이메일 주소를 적어 보내신 경우만 예외'), '6항의 익명 응시 문단에 후기 이메일의 예외가 없다');
  assert.ok(rights6.includes('같은 응시에서 따로 보관한 음성이나 분석 정보가 있으면 함께 요청하실 수 있고'), '6항에 후기 이메일로 음성과 분석 정보를 요청하는 방법이 없다');
  // 재응시의 즉시 삭제가 따로 보관하는 음성과 분석 정보까지 지운다고 읽히지 않는다.
  const session2 = section('<h3>테스트 세션</h3>', '<h3>계정 (앱 소셜 로그인)</h3>').replace(/\s+/g, ' ');
  assert.ok(session2.includes('따로 보관하는 음성과 분석 정보, 단어 답안 기록은 이때 삭제되지 않습니다'), '재응시의 즉시 삭제 문장에 보관 정보의 예외가 없다');
  // 위탁 표의 AWS 업무에 분석 정보 저장이 들어 있다.
  const trust = section('<h2>3. 개인정보 처리의 위탁', '<h2>4. 개인정보의 국외 이전').replace(/\s+/g, ' ');
  assert.ok(trust.includes('선택 동의하지 않으신 응시의 분석 정보와 단어 답안 기록 저장소'), '3항 위탁 표에 분석 정보 저장이 없다');
  // 음성 미보존 문장은 그대로다 - 남기는 것은 분석 정보이지 음성이 아니다.
  assert.ok(flat.includes('동의하지 않으신 경우, 음성은 데이터베이스나 S3 같은 영속 저장소에 저장하지 않습니다'), '음성 미보존 문장이 사라졌다');
});

test('단어 답안 기록 절이 있고 대상, 항목, 장소, 기간이 코드와 맞는다 (KAN-276, S3VocabAnswerSampleStore)', () => {
  assert.ok(html.includes('<h3>단어 답안 기록</h3>'), '단어 답안 기록 절이 없다');
  const record = section('<h3>단어 답안 기록</h3>', '<h3>테스트 세션</h3>').replace(/\s+/g, ' ');
  assert.ok(record.includes('단어 문항의 난이도를 측정'), '목적이 없다');
  assert.ok(record.includes('선택 동의하셨는지와 상관없이 브라우저 웹과 앱 모두'), '대상(동의와 무관, 웹과 앱)이 없다');
  // 메타 JSON의 필드 (S3VocabAnswerSampleStore.metadata). 필드가 늘면 여기와 본문을 함께 늘린다.
  for (const item of [
    '답안 식별자', '세션 식별자', '문항 식별자', '출신 지역', '테스트 버전', '채점 버전', '고르신 선택지',
    '그 문항의 정답', '정답 여부', '답하신 시각', '저장 시각',
  ]) {
    assert.ok(record.includes(item), `단어 답안 기록 절의 항목에 「${item}」이 없다`);
  }
  assert.ok(record.includes('가명으로 바꾸지 않고 그대로 저장'), '식별자를 그대로 저장한다는 말이 없다');
  assert.ok(record.includes('Amazon S3'), '보관 장소가 없다');
  assert.ok(record.includes('측정 목적 달성 시까지'), '보유 기간이 없다');
  // 음성 저장에 동의한 계정 세션은 training_voice_owner가 세션과 계정을 계속 잇는다 (TrainingVoiceOwners.record) -
  // 같은 세션 식별자의 단어 기록도 계정으로 찾을 수 있으므로 「찾을 수 없다」만 적으면 거짓 고지다 (Codex 리뷰 P2).
  assert.ok(record.includes('음성 저장에 동의하신 응시도 계정과 세션의 연결 기록이 남으므로'), '계정 연결 기록의 예외가 없다');
  const labels = section('<h3>선택 동의하지 않으신 경우에 남기는 분석 정보</h3>', '<h3>단어 답안 기록</h3>').replace(/\s+/g, ' ');
  assert.ok(labels.includes('계정과 세션의 연결 기록이 남으므로'), '분석 정보 절에 계정 연결 기록의 예외가 없다');
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리').replace(/\s+/g, ' ');
  assert.ok(disposal.includes('단어 답안 기록: 측정 목적 달성 시까지'), '5항에 단어 답안 기록의 보유가 없다');
});

test('사투리 번역기 절이 있고 구글 전송, 보관 항목, 장소, 기간, 탈퇴 처리가 코드와 맞는다 (KAN-266, S3TranslationRecordStore)', () => {
  assert.ok(html.includes('<h3>사투리 번역기 (앱)</h3>'), '사투리 번역기 절이 없다');
  const translator = section('<h3>사투리 번역기 (앱)</h3>', '<h3>테스트 세션</h3>').replace(/\s+/g, ' ');
  assert.ok(translator.includes('로그인해 이용하는 앱에서만'), '대상(로그인한 앱)이 없다');
  assert.ok(translator.includes('Google LLC의 Gemini API로 보냅니다'), '구글 전송이 없다');
  assert.ok(translator.includes('제품의 개선에 쓸 수 있고 사람이 검토할 수 있습니다'), '구글의 제품 개선 이용과 사람 검토가 없다');
  assert.ok(translator.includes('개인정보나'), '개인정보를 넣지 말라는 안내가 없다');
  // 기록 객체의 필드 (S3TranslationRecordStore.body). 필드가 늘면 여기와 본문을 함께 늘린다.
  for (const item of [
    '요청 식별자', '요청 시각', '입력하신 문장', '번역 결과 문장', '처리 결과 종류', '처리 시간', '번역 모델의 이름',
    '대체 식별자',
  ]) {
    assert.ok(translator.includes(item), `사투리 번역기 절의 항목에 「${item}」이 없다`);
  }
  assert.ok(translator.includes('번역이 되지 않은 요청'), '실패 요청도 남긴다는 말이 없다 (결과 종류와 상관없이 전부 저장)');
  assert.ok(translator.includes('계정의 식별자를 적지 않고'), '계정 ID 대신 대체 ID라는 말이 없다');
  assert.ok(translator.includes('탈퇴하시면 그 연결 기록을 지웁니다'), '탈퇴 때 연결을 끊는다는 말이 없다 (WithdrawalService)');
  assert.ok(translator.includes('서버 운영 로그에 남기지 않습니다'), '로그에 텍스트를 남기지 않는다는 말이 없다');
  assert.ok(translator.includes('Amazon S3') && translator.includes('ap-northeast-2'), '보관 장소가 없다');
  assert.ok(translator.includes('기간 제한 없이 보관'), '보유 기간이 없다 (만료 없음)');

  const provision = section('<h2>2. 개인정보의 제3자 제공', '<h2>3. 개인정보 처리의 위탁').replace(/\s+/g, ' ');
  assert.ok(provision.includes('Google LLC (Gemini API)'), '2항에 Gemini API 제공이 없다');
  const outsourcing = section('<h2>3. 개인정보 처리의 위탁', '<h2>4. 개인정보의 국외 이전').replace(/\s+/g, ' ');
  assert.ok(outsourcing.includes('번역 기록 저장소'), '3항 AWS 행에 번역 기록 저장소가 없다');
  const transfer = section('<h2>4. 개인정보의 국외 이전', '<h2>5. 개인정보의 파기').replace(/\s+/g, ' ');
  assert.ok(transfer.includes('<dd>Google LLC (Gemini API)</dd>'), '4항에 Gemini API 이전 내역이 없다');
  for (const term of ['이전되는 국가', '이전 일시와 방법', '번역기에 입력하신 문장']) {
    assert.ok(transfer.includes(term), `4항 Gemini 이전 내역에 「${term}」이 없다`);
  }
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리').replace(/\s+/g, ' ');
  assert.ok(disposal.includes('번역 기록: 기간 제한 없이 보관'), '5항에 번역 기록의 보유가 없다');
  const rights = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세').replace(/\s+/g, ' ');
  assert.ok(rights.includes('사투리 번역기의 번역 기록은 탈퇴 전이라면'), '6항에 번역 기록 요청 방법이 없다');
});

test('앱이 로그인으로만 이용된다는 문장이 남아 있지 않다 (KAN-274)', () => {
  // 첫 스토어 심사 빌드에는 로그인이 없다. 조건 없는 문장이 한 자리라도 남으면 그 빌드의 이용자에게 거짓 고지다.
  const flat = html.replace(/\s+/g, ' ');
  for (const sentence of [
    '앱(Android, iOS)은 구글, 카카오, 네이버, 애플(iOS만) 중 하나로 로그인한 계정으로 이용합니다',
    '<p> 앱은 구글, 카카오, 네이버, 애플(iOS만) 중 하나로 로그인해 이용합니다',
    '<p> 앱은 처음 로그인할 때 이 방침에 대한 동의를 받습니다',
    '브라우저 웹의 세션은 익명이고, 앱의 세션은 아래 「계정」에 적은 대로 계정에 연결됩니다',
  ]) {
    assert.ok(!flat.includes(sentence), `앱이 로그인으로만 이용된다는 문장이 남아 있다: ${sentence}`);
  }
});

// ── 계정 PII (KAN-240) ───────────────────────────────────────────────────────
// 앱 소셜 로그인(KAN-223)이 이메일, 이름, 생년월일 같은 개인 식별 정보를 받기 시작했다. 방침이 계정을
// 모르던 시절의 문장(「계정이 없습니다」)이 남으면 고지와 실제가 정반대가 되므로, 계정 고지가 서야 하는
// 자리(1, 3, 5, 6, 7, 12항)를 각각 붙든다.

test('「계정이 없다」 계열 문장이 남아 있지 않다 (KAN-240)', () => {
  const flat = html.replace(/\s+/g, ' ');
  for (const sentence of [
    '서비스에는 회원가입과 로그인, 계정이 없습니다',
    '서비스에는 계정과 개인을 알아볼 수 있는 값이 없습니다',
    '계정이 없어 나이를 확인하지 않',
    '이름, 연락처, 생년월일 같은 이용자를 직접 알아볼 수 있는 정보를 수집하지 않',
  ]) {
    assert.ok(!flat.includes(sentence), `계정이 없던 시절의 문장이 남아 있다: ${sentence}`);
  }
});

test('1항에 계정 절이 있고 수집 항목과 목적과 보유 기간을 적는다 (KAN-240, V2__app_user.sql)', () => {
  assert.ok(html.includes('<h3>계정 (앱 소셜 로그인)</h3>'), '1항 아래 「계정」 절이 없다');
  const account = section('<h3>계정 (앱 소셜 로그인)</h3>', '<h3>익명 통계</h3>').replace(/\s+/g, ' ');
  // app_user의 열 전부다. 열이 늘면 여기와 본문을 함께 늘린다.
  for (const item of ['이메일', '이름', '생년월일', '성별', '출신 지역', '닉네임', '프로필 이미지', '식별값', '동의하신 시각과 방침 버전',
    // V6__voice_consent.sql의 세 열 (KAN-269).
    '음성 저장 선택 동의 기록']) {
    assert.ok(account.includes(item), `계정 절에 「${item}」이 없다`);
  }
  assert.ok(account.includes('탈퇴하실 때까지'), '계정 절에 보유 기간(탈퇴까지)이 없다');
  assert.ok(account.includes('만 14세 미만 가입 제한'), '생년월일의 목적(만 14세 미만 가입 제한)이 없다');
  // 앱 세션은 계정에 붙지만 24시간 규칙은 같다. 로그에 세션과 계정의 대응을 남기지 않는 것은
  // SessionService의 「세션 생성」 로그가 지킨다 (AuthApiTest의 세션 생성 로그 검사).
  assert.ok(account.includes('계정에 지난 결과가 쌓이지 않습니다'), '계정 세션의 보유 기간 서술이 없다');
  assert.ok(account.includes('어느 세션이 어느 계정의 것인지를 남기지 않습니다'), '로그의 세션-계정 대응 미기록 문구가 없다');
});

test('3항에 로그인 제공자 넷과 받는 정보, 애플 전달용 이메일이 있다 (KAN-240, auth/*IdpVerifier.java)', () => {
  const idp = section('<h3>소셜 로그인 제공자로부터 받는 정보</h3>', '<h2>4. 개인정보의 국외 이전');
  for (const provider of ['구글', '카카오', '네이버', '애플']) {
    assert.ok(idp.includes(provider), `3항 제공자 표에 ${provider}가 없다`);
  }
  assert.ok(idp.includes('privaterelay.appleid.com'), '애플 전달용 이메일 설명이 없다');
  assert.ok(idp.includes('휴대전화 번호는 받더라도 저장하지 않습니다'), '네이버 휴대전화 번호 미저장 문구가 없다');
});

test('5, 6, 7, 12항이 계정 기준으로 다시 쓰였다 (KAN-240)', () => {
  const disposal = section('<h2>5. 개인정보의 파기', '<h2>6. 정보주체의 권리').replace(/\s+/g, ' ');
  assert.ok(disposal.includes('계정 정보: 탈퇴하시면 지체 없이 파기'), '5항에 계정 파기가 없다');
  assert.ok(disposal.includes('로그인 토큰'), '5항에 로그인 토큰 폐기가 없다');

  const rights = section('<h2>6. 정보주체의 권리', '<h2>7. 만 14세 미만').replace(/\s+/g, ' ');
  assert.ok(rights.includes('탈퇴'), '6항에 탈퇴 방법이 없다');
  // 탈퇴 API(KAN-241)가 나오기 전까지의 임시 절차 - 보호책임자 이메일로 요청하고 계정 이메일로 본인 확인.
  assert.ok(rights.includes('계정에 등록된 이메일 주소로 13항의 이메일에 탈퇴를 요청'), '6항에 임시 탈퇴 절차가 없다');

  const children = section('<h2>7. 만 14세 미만', '<h2>8. 개인정보 자동 수집 장치').replace(/\s+/g, ' ');
  assert.ok(children.includes('생년월일로 나이를 확인해 만 14세 미만이면 가입을 거절'), '7항에 생년월일 확인 방식이 없다');

  const consent = section('<h2>12. 동의를 받는 방식', '<h2>13. 개인정보 보호책임자').replace(/\s+/g, ' ');
  assert.ok(consent.includes('앱은 처음 로그인할 때 이 방침에 대한 동의를 받습니다'), '12항에 가입 동의가 없다');
});

// ── 방침 버전 = 서버가 받는 동의 버전 (KAN-240) ─────────────────────────────────
// 앱은 가입할 때 동의한 방침 버전을 보내고, 서버는 AccenturyProperties.Auth.PRIVACY_POLICY_VERSION과
// 정확히 같을 때만 동의로 기록한다. 게시본의 버전이 서버 기본값과 어긋나면 이용자가 읽은 문서와 서버가
// 기록하는 동의가 다른 문서를 가리키게 된다.
const PROPERTIES = join(HERE, '..', '..', 'backend', 'src', 'main', 'java', 'app', 'accentury', 'backend', 'common',
  'AccenturyProperties.java');
const RESOURCES = join(HERE, '..', '..', 'backend', 'src', 'main', 'resources');

function publishedVersion(body) {
  const match = body.match(/<meta name="accentury-policy-version" content="([^"]+)" \/>/);
  assert.ok(match, 'accentury-policy-version 메타가 없다');
  return match[1];
}

test('게시 HTML의 방침 버전이 BE 설정 기본값과 같다 (KAN-240)', () => {
  const source = readFileSync(PROPERTIES, 'utf8');
  const constant = source.match(/PRIVACY_POLICY_VERSION = "([^"]+)";/);
  assert.ok(constant, 'AccenturyProperties에 PRIVACY_POLICY_VERSION 상수가 없다');
  assert.ok(source.includes('@DefaultValue(Auth.PRIVACY_POLICY_VERSION) String privacyPolicyVersion'),
    'Auth.privacyPolicyVersion의 기본값이 상수를 가리키지 않는다');
  assert.equal(publishedVersion(html), constant[1], '게시 HTML의 방침 버전과 BE 기본값이 다르다');
  // 버전은 app_user.privacy_policy_version varchar(32)에 들어간다.
  assert.ok(constant[1].length <= 32, '방침 버전이 32자를 넘는다');
});

test('application.yml이 방침 버전을 덮어쓰지 않는다 (KAN-240)', () => {
  // yml이 값을 정하면 위 테스트가 보는 기본값은 쓰이지 않는 값이 된다. 버전의 정본은 상수 하나다.
  for (const name of readdirSync(RESOURCES).filter((f) => /^application.*\.ya?ml$/.test(f))) {
    const yml = readFileSync(join(RESOURCES, name), 'utf8');
    assert.ok(!/^\s*privacy-policy-version\s*:/m.test(yml), `${name}이 privacy-policy-version을 정한다`);
  }
});

test('시행일 표기 두 자리가 방침 버전과 같고, 두 환경 게시본 모두 버전 메타를 싣는다 (KAN-240)', () => {
  const version = publishedVersion(html);
  const stamps = [...html.matchAll(/시행일: ([0-9-]+) \(방침 버전 ([0-9-]+)\)/g)];
  assert.equal(stamps.length, 2, '시행일 표기가 머리와 14항 두 자리에 있어야 한다');
  for (const [, effective, stamped] of stamps) {
    assert.equal(effective, version, '시행일이 방침 버전과 다르다');
    assert.equal(stamped, version, '본문에 적힌 방침 버전이 메타와 다르다');
  }
  for (const env of ['prod', 'staging']) {
    assert.equal(publishedVersion(rendered(env)), version, `${env} 게시본의 방침 버전이 다르다`);
  }
});
