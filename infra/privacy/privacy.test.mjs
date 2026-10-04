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
  assert.ok(html.includes('즉시 삭제'), '음성 즉시 삭제 문구가 없다');
});

test('임시 파일 청소 기준 30분이 적혀 있다 (KAN-27, ai temp-retention: 30m)', () => {
  // 세션 토큰 수명(30m, backend application.yml)도 같은 값이라 문서에서 두 자리에 쓰인다.
  assert.ok(html.includes('30분'), '30분 기준이 없다');
});

test('합본 분석 전 음성의 임시 메모리 보관이 적혀 있다 (KAN-261, KAN-262 Redis 문항별 칸)', () => {
  // 음성 3문항을 한 번에 분석하므로 앞 문항 음성이 Redis(영속화 꺼짐)에 세션 만료(30분)까지 머문다.
  // 이 보관을 빼거나 「디스크에 기록」 쪽으로 바뀌면 「영속 저장소에 저장하지 않는다」가 거짓이 된다.
  const voice = section('<h3>음성 녹음</h3>', '<h3>테스트 세션</h3>').replace(/\s+/g, ' ');
  assert.ok(voice.includes('임시 메모리 저장소'), '음성 임시 메모리 보관 문구가 없다');
  assert.ok(voice.includes('디스크에 기록하지 않는 메모리 전용'), '메모리 전용(디스크 미기록) 문구가 없다');
  assert.ok(voice.includes('세션이 만료되는 30분 뒤에 자동으로 삭제'), '미완주 시 30분 뒤 삭제 문구가 없다');
  assert.ok(voice.includes('이전 녹음은 그 자리에서 새 녹음으로 바뀝니다'), '재녹음 시 교체 문구가 없다');
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
  assert.ok(html.includes('ap-northeast-2'), '리전 표기가 없다');
  assert.ok(html.includes('서울 리전'), '서울 리전 표기가 없다');
});

// ── 환경별 본문 (KAN-239) ─────────────────────────────────────────────────────
// staging에는 학습 수집 고지가 붙고 prod에는 없다. 자르는 규칙은 게시 스크립트 하나에 있고
// (--render), 여기서는 그 스크립트를 그대로 돌려 두 환경에 올라갈 본문을 검사한다.
const SCRIPT = join(HERE, '..', '..', 'scripts', 'publish-privacy.sh');
const rendered = (env) => execFileSync('bash', [SCRIPT, '--render', env], { encoding: 'utf8' });

test('staging-only 표식은 짝이 맞고 각자 한 줄을 차지한다 (KAN-239)', () => {
  // 스크립트는 줄 단위로 자른다. 표식이 다른 내용과 한 줄에 있거나 짝이 안 맞으면 prod 본문의
  // 뒷부분이 통째로 잘리거나 고지가 prod에 샌다.
  const lines = html.split('\n');
  const begins = lines.filter((line) => line.includes('staging-only:begin'));
  const ends = lines.filter((line) => line.includes('staging-only:end'));
  assert.equal(begins.length, ends.length, '표식의 짝이 맞지 않는다');
  assert.ok(begins.length >= 1, 'staging-only 블록이 없다');
  for (const line of [...begins, ...ends]) {
    assert.match(line, /^<!-- staging-only:(begin|end) -->$/, `표식이 한 줄을 통째로 차지하지 않는다: ${line}`);
  }
});

test('prod 본문에는 학습 수집 고지가 없고 음성 미보존 문구가 그대로다 (KAN-239, FR-DP-01)', () => {
  const prod = rendered('prod');
  assert.ok(!prod.includes('staging-only'), 'prod 본문에 표식이 남았다');
  assert.ok(!prod.includes('내부 테스트 환경'), 'prod 본문에 staging 고지가 새었다');
  assert.ok(prod.includes('음성은 데이터베이스나 S3 같은 영속 저장소에 저장하지 않습니다'), 'prod 본문의 음성 미보존 문구가 없다');
  assert.ok(prod.includes('</html>'), 'prod 본문 뒷부분이 잘렸다');
});

test('staging 본문은 학습 수집의 목적과 대상과 기간을 적는다 (KAN-239)', () => {
  const staging = rendered('staging');
  // 본문은 줄바꿈으로 감싸여 있어 낱말 사이 공백을 하나로 접어 대조한다.
  const notice = staging.slice(staging.indexOf('staging-only:begin'), staging.indexOf('staging-only:end'))
    .replace(/\s+/g, ' ');
  assert.ok(notice.includes('학습'), '목적(모델 재학습)이 없다');
  assert.ok(notice.includes('동의한 테스터 계정'), '대상(동의한 테스터 계정)이 없다');
  assert.ok(notice.includes('로그인하지 않은 응시'), '익명 응시를 보관하지 않는다는 문구가 없다');
  assert.ok(notice.includes('보유 기간이 끝나는 날'), '보관 기간이 없다');
  assert.ok(notice.includes('가명'), '세션 가명화 문구가 없다');
  assert.ok(notice.includes('철회'), '철회 방법이 없다');
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
  for (const item of ['이메일', '이름', '생년월일', '성별', '출신 지역', '닉네임', '프로필 이미지', '식별값', '동의하신 시각과 방침 버전']) {
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
