// SPA 경로 재작성 (KAN-126). 기본 동작(S3 오리진)에만 붙는 viewer-request 함수.
// 확장자 없는 경로 직접 진입을 /index.html로 돌린다. /v0/*는 이 함수가 붙지 않는
// 별도 동작이라 API 오류 봉투가 치환될 일이 없다. 루트만 예외로 사투리 이야기 목록을 내준다 (KAN-346).

// 루트(`/`)에서 SPA를 내주는 쿼리 키 (KAN-346). SPA가 직접 읽는 값만 둔다. 이유는 아래 루트 갈래의 주석.
var SPA_ROOT_KEYS = ['bridge', 'screen', 'c'];

// querystring 객체를 `?a=1&b=2` 문자열로 되돌린다. 값은 CloudFront가 넘긴 그대로(인코딩된 채) 붙인다.
// 같은 키가 여러 번 오면 multiValue에 전부 있다. 쿼리가 없으면 빈 문자열이다.
function toQueryString(query) {
  var parts = [];
  for (var key in query) {
    var values = query[key].multiValue || [query[key]];
    for (var i = 0; i < values.length; i++) {
      parts.push(values[i].value === '' ? key : key + '=' + values[i].value);
    }
  }
  return parts.length === 0 ? '' : '?' + parts.join('&');
}

function handler(event) {
  var request = event.request;

  // /.well-known/ 아래는 손대지 않는다 (KAN-32 4단계). App Links 검증 파일
  // apple-app-site-association은 확장자가 없어서 아래 규칙에 걸려 index.html이 되는데,
  // 그러면 애플 CDN이 JSON 대신 HTML을 받아 Universal Link 검증이 성립하지 않는다.
  // assetlinks.json은 점이 있어 원래 영향을 받지 않지만, /.well-known/ 자체가 RFC 8615가
  // 기계 판독 메타데이터용으로 예약한 접두사라 그 아래는 통째로 예외로 둔다.
  if (request.uri.startsWith('/.well-known/')) {
    return request;
  }

  // 루트는 사투리 이야기 목록이다 (KAN-346). 읽을 글이 있는 문서를 사이트의 첫 화면으로 내주고,
  // 테스트 화면(SPA)은 /test 같은 확장자 없는 경로로 연다. S3의 키는 옮기지 않고 여기서만 가른다.
  //
  // 쿼리에 SPA가 읽는 키(SPA_ROOT_KEYS)가 있으면 지금처럼 SPA다.
  // - bridge: 앱 WebView가 항상 싣는 값이다. 스토어에 나간 앱이 루트에 쿼리를 붙여 열기 때문에
  //   (Accentury_App의 buildWebUrl), 이 갈래가 빠지면 고칠 수 없는 기존 앱의 화면에 글 목록이 뜬다.
  // - screen: 결과 화면처럼 SPA가 만든 옛 루트 주소가 싣는다.
  // - c: 공유 유입 계측 코드다 (Accentury_App web/src/session/campaign.ts). 목록으로 보내면 목록의
  //   테스트 버튼이 쿼리 없는 /test라 값이 사라져 유입 경로가 끊긴다.
  // 값이 비어 있어도(`?bridge`) 키가 있으면 SPA다 - CloudFront는 빈 값도 키로 넘긴다.
  // utm_* 같은 추적 쿼리만 붙은 루트는 목록이다. 밖에서 들어오는 링크가 첫 화면을 보는 것이 맞다.
  var query = request.querystring || {};
  if (request.uri === '/') {
    var wantsApp = false;
    for (var i = 0; i < SPA_ROOT_KEYS.length; i++) {
      if (query[SPA_ROOT_KEYS[i]] !== undefined) wantsApp = true;
    }
    request.uri = wantsApp ? '/index.html' : '/guide/index.html';
    return request;
  }

  // 목록의 옛 주소는 루트로 보낸다 (KAN-346). 같은 글이 두 주소에 있으면 검색 엔진이 중복 문서로 본다.
  // 확장자 없는 /guide와 /guide/도 함께 보낸다 - 그대로 두면 아래 규칙이 SPA를 내줘서 목록의 주소에
  // 테스트 화면이 뜬다.
  // 쿼리는 그대로 싣는다. 버리면 utm이 붙은 옛 링크의 유입이 분석 도구에서 직접 방문으로 잡힌다.
  // 위 갈래가 uri를 바꾸는 것은 CloudFront 안쪽의 일이라 이 분기를 다시 타지 않는다.
  // max-age를 짧게 주는 이유는 브라우저가 301을 기한 없이 기억하기 때문이다. 전환을 되돌려도 한 시간
  // 뒤에는 옛 주소가 다시 열린다.
  if (request.uri === '/guide/index.html' || request.uri === '/guide' || request.uri === '/guide/') {
    return {
      statusCode: 301,
      statusDescription: 'Moved Permanently',
      headers: {
        location: { value: '/' + toQueryString(query) },
        'cache-control': { value: 'public, max-age=3600' },
      },
    };
  }

  var lastSegment = request.uri.split('/').pop();
  if (!lastSegment.includes('.')) {
    request.uri = '/index.html';
  }
  return request;
}
