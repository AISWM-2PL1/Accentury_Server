// SPA 경로 재작성 (KAN-126). 기본 동작(S3 오리진)에만 붙는 viewer-request 함수.
// 확장자 없는 경로 직접 진입을 /index.html로 돌린다. /v0/*는 이 함수가 붙지 않는
// 별도 동작이라 API 오류 봉투가 치환될 일이 없다. 루트만 예외로 사투리 이야기 목록을 내준다 (KAN-346).
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
  // 쿼리에 bridge나 screen이 있으면 지금처럼 SPA다. bridge는 앱 WebView가 항상 싣는 값이다 -
  // 스토어에 나간 앱이 루트에 쿼리를 붙여 열기 때문에(Accentury_App의 buildWebUrl), 이 갈래가 빠지면
  // 앱 화면에 글 목록이 뜬다. screen은 결과 화면처럼 SPA가 만든 옛 루트 주소가 싣는다.
  // 값이 비어 있어도(`?bridge`) 키가 있으면 SPA다 - CloudFront는 빈 값도 키로 넘긴다.
  if (request.uri === '/') {
    var query = request.querystring || {};
    var wantsApp = query.bridge !== undefined || query.screen !== undefined;
    request.uri = wantsApp ? '/index.html' : '/guide/index.html';
    return request;
  }

  // 목록의 옛 주소는 루트로 보낸다 (KAN-346). 같은 글이 두 주소에 있으면 검색 엔진이 중복 문서로 본다.
  // 위 갈래가 uri를 바꾸는 것은 CloudFront 안쪽의 일이라 이 분기를 다시 타지 않는다.
  // max-age를 짧게 주는 이유는 브라우저가 301을 기한 없이 기억하기 때문이다. 전환을 되돌려도 한 시간
  // 뒤에는 옛 주소가 다시 열린다.
  if (request.uri === '/guide/index.html') {
    return {
      statusCode: 301,
      statusDescription: 'Moved Permanently',
      headers: {
        location: { value: '/' },
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
