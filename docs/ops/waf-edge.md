# WAF와 엣지 보안

CloudFront 앞단 WAF 웹 ACL(KAN-149, KAN-244)과 전송 구간, 엣지 보안 설정 묶음(KAN-245)의 운영 절차와 지금 값이다.

관련 코드:

- [`infra/modules/waf`](../../infra/modules/waf) - 웹 ACL, 규칙 7개, 로그 그룹 (us-east-1), 경로 정규식 테스트 `waf-regex.test.mjs`
- [`infra/modules/edge`](../../infra/modules/edge) - CloudFront 배포의 `web_acl_id`, 보안 응답 헤더, web 버킷 TLS 강제
- [`infra/modules/ai-host`](../../infra/modules/ai-host) - 부팅 자산 버킷 TLS 강제
- [`infra/modules/network`](../../infra/modules/network) - backend 아웃바운드 규칙
- [`infra/modules/config`](../../infra/modules/config) - `SPRING_DATASOURCE_URL`(sslmode=verify-full)
- [`backend/Dockerfile`](../../backend/Dockerfile) - RDS 전역 CA 번들
- [`infra/bootstrap/audit.tf`](../../infra/bootstrap/audit.tf), [`infra/bootstrap/voice.tf`](../../infra/bootstrap/voice.tf), [`infra/bootstrap/main.tf`](../../infra/bootstrap/main.tf) - CloudTrail, GuardDuty, 음성과 tfstate 버킷 TLS 강제
- [`infra/envs/prod/main.tf`](../../infra/envs/prod/main.tf) - 번역 기록 버킷 TLS 강제
- `infra/envs/*/terraform.tfvars` - `waf_enforce`, `waf_rate_limit`, `waf_auth_rate_limit`, `waf_admin_rate_limit`, `waf_ip_reputation_enforce`

실증 기록은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-149, KAN-169, KAN-244 항목에, 결정 이유는 [docs/wiki/infra-decisions.md](../wiki/infra-decisions.md)에 있다.

## WAF 웹 ACL (KAN-149, KAN-244)

`modules/waf`가 us-east-1에 CLOUDFRONT 스코프 웹 ACL과 로그 그룹을 만들고, `modules/edge`가
배포의 `web_acl_id`에 그 ARN을 넣는다. envs는 `providers = { aws = aws.us_east_1 }`로
모듈을 호출한다. 서울 리전에 만들면 배포의 WAF 목록에 나타나지 않고 오류도 없다.

| 우선순위 | 규칙 | 하는 일 | 예외 |
| --- | --- | --- | --- |
| 10 | `rate-limit-costly-posts` | IP당 5분 창에 `POST /v0/sessions` + 음성 업로드 두 경로(`POST …/recording`, `POST …/recordings`) + `POST …/feedback` 합산 `waf_rate_limit`건 초과 시 429 | 폴링, 어휘 답안, 단어 학습, 정적 자산은 세지 않음 |
| 15 | `rate-limit-auth` | IP당 5분 창에 `POST /v0/auth/login` + `POST /v0/auth/refresh` 합산 `waf_auth_rate_limit`건 초과 시 429 (KAN-244) | 다른 메서드는 세지 않음 |
| 16 | `rate-limit-admin` | IP당 5분 창에 `/admin/` 아래 요청 `waf_admin_rate_limit`건 초과 시 429 (KAN-244) | 없음 (메서드 무관) |
| 20 | `aws-common` | `AWSManagedRulesCommonRuleSet` (본문 8KB 상한, XSS, LFI/RFI, 경로 조작 등. SQLi 규칙은 이 그룹에 없음) | 본문 규칙 5개(`SizeRestrictions_BODY`, `CrossSiteScripting_BODY`, `GenericLFI_BODY`, `GenericRFI_BODY`, `EC2MetaDataSSRF_BODY`)만 Count로 바꿔 라벨만 남김. 나머지 검사는 모든 경로에 그대로 |
| 21 | `aws-common-body` | 20번이 남긴 본문 규칙 라벨이 붙은 요청을 403 (KAN-299) | 음성 업로드 두 경로는 제외 (multipart 음성이 본문 8KB 규칙에 걸리므로). 그 경로도 20번의 비본문 검사는 받음 |
| 30 | `aws-known-bad-inputs` | `AWSManagedRulesKnownBadInputsRuleSet` (Log4j, Java 역직렬화 등) | 없음 |
| 40 | `aws-ip-reputation` | `AWSManagedRulesAmazonIpReputationList` (아마존 위협 인텔리전스의 봇, 정찰, DDoS 출처 IP) (KAN-244) | 없음. `waf_enforce`와 `waf_ip_reputation_enforce`가 둘 다 true일 때만 차단 |

지금 값은 두 환경 같다.

| 변수 | 값 |
| --- | --- |
| `waf_enforce` | `true` (Block) |
| `waf_rate_limit` | 400 |
| `waf_auth_rate_limit` | 100 |
| `waf_admin_rate_limit` | 50 |
| `waf_ip_reputation_enforce` | `false` (IP 평판 규칙만 Count) |

### 음성 업로드 경로 예외

음성 업로드 경로는 둘이다. 레벨테스트 `POST /v0/sessions/{sessionId}/voice-items/{itemId}/recording`과
억양 학습 `POST /v0/learning/intonation-cards/{cardId}/recordings`. `recording_path_regex` 하나가 두 경로를
앞뒤 앵커로 고정해 받고, 21번 규칙의 제외와 10번 규칙의 집계에 같이 쓰인다. 끝 세그먼트만 보지 않는
이유는 끝이 경로 변수인 GET 경로(`/v0/tests/{testVersion}`, `/v0/learning/intonation-attempts/{attemptId}`
등)에 값 `recordings`를 넣는 것만으로 예외를 받기 때문이다. 둘을 한 정규식에 두는 이유는 둘 다 multipart
음성이고 건마다 AI 추론을 쓰는 같은 비용 모양이라 예외와 집계를 같이 받아야 해서다.

본문 규칙을 그룹 전체 제외가 아니라 라벨로 거르는 이유는 업로드 경로도 UA 없음, 나쁜 봇 UA, 쿼리와
URI의 XSS/LFI/RFI, 경로 조작 검사는 계속 받게 하기 위해서다. 억양 업로드는 자유 문자열 `meta` 파트를
받는데, 본문 검사가 빠진 자리는 backend 검증(멱등 키, meta 필수, 크기 상한 2MB, WAV 헤더)이 받친다.
대가는 로그다. 로그 필터가 Count 매치도 남기므로 8KB를 넘는 음성 업로드는 건마다 `aws-common`의
COUNT 한 줄(라벨 `SizeRestrictions_Body`)이 남는다. 업로드는 하루 수백 건이라 비용은 1달러 미만이다.

새 음성 업로드 경로를 만들 때는 `recording_path_regex`에 그 경로를 더하고 `waf-regex.test.mjs`에 매치
케이스를 넣는다. 어긋나면 21번 규칙이 업로드를 전부 403으로 막는데 화면은 멀쩡해 원인을 찾기 어렵다.
배포 스모크(`scripts/e2e_smoke.py`)가 토큰 없는 8KB 초과 업로드로 억양 학습 경로의 통과(401)를 매 배포
확인하고, 레벨테스트 경로는 본 시나리오가 올린다.

차단 응답은 backend의 429 봉투와 같은 JSON(`RATE_LIMITED`, `retryable: true`,
`retryAfterMs: 300000`) + `Retry-After: 300`이다. 앱과 웹이 backend 429와 똑같이
처리한다. 대기 시간이 평가 창(5분)과 같은 이유는 한 번 넘긴 IP의 요청이 창에서
빠질 때까지 차단이 이어지기 때문이다. 관리형 규칙의 차단은 WAF 기본 403(본문
없음)이다. 경로 비교는 `URL_DECODE` + `NORMALIZE_PATH` 뒤에 정규식으로 하고, 세그먼트
뒤의 matrix 파라미터(`;x=1`)를 허용한다. Tomcat과 Spring이 `%73essions`, `./`, `;x=1`을
풀어 라우팅하므로, 변환 없이는 변형 경로가 컨트롤러에는 닿으면서 집계만 비껴간다
(backend `UploadRateLimitFilter`가 같은 이유로 파싱된 경로로 매칭한다).

로그 그룹은 us-east-1의 `aws-waf-logs-accentury-<env>`(output `waf_log_group`)다. 기본 Allow는 로그 필터가 버리므로 남는 건 매치된 요청뿐이다.

### Count 관찰 후 Block 전환

새 규칙을 넣거나 환경을 처음 지을 때의 절차다. 지금 두 환경은 이 절차를 마치고 Block이다.

1. `waf_enforce = false`로 apply한다. IP 평판을 뺀 다섯 규칙이 Count라 아무것도 막지 않고
   매치만 로그에 남긴다(IP 평판 규칙은 `waf_ip_reputation_enforce`도 true여야 차단하므로 이때도 Count다).
2. 실제 녹음 파일로 앱 전 구간(세션 생성, 업로드 10건 이상, 완료, 결과)을 돌린다.
3. us-east-1 CloudWatch Logs Insights에서 로그 그룹 `aws-waf-logs-accentury-<env>`
   (output `waf_log_group`)에 다음을 돌린다.

   ```
   fields @timestamp, httpRequest.httpMethod as method, httpRequest.uri as uri,
          httpRequest.clientIp as ip, action, terminatingRuleId
   | parse @message /"ruleId":"(?<rule>[^"]+)"/
   | stats count() by method, uri, rule
   | sort count desc
   ```

   업로드 경로(`…/recording`, `…/recordings`)에 `aws-common-body` 매치가 0건이어야 한다. `aws-common`의
   COUNT(본문 규칙 라벨)는 업로드마다 남는 것이 정상이다. `aws-common-body` 매치가 있으면
   스코프 다운이 빠진 것이다. 다른 경로의 매치는 규칙 이름을 티켓에 기록하고 오탐
   여부를 판단한다.
4. 오탐 건수와 규칙을 티켓에 기록한 뒤 `waf_enforce = true`로 apply한다.
   staging 먼저, 기록 뒤 prod.
5. 전환 확인: 같은 IP에서 `POST /v0/sessions`를 `waf_rate_limit`건 넘게 보내면 429와
   위 JSON 봉투가 오고, 로그에 `action = BLOCK`, `terminatingRuleId =
   rate-limit-costly-posts`가 남는다. WAF는 약 10초 간격으로 집계하므로 넘긴 직후
   몇 건은 통과한다. 차단은 IP의 5분 창 요청 수가 임계값 아래로 내려가면 풀린다.

prod는 staging의 Count 관찰과 Block 전환 실증(KAN-169) 뒤 처음부터 Block으로 지었다.

### 인증 경로 rate 규칙 (KAN-244)

`waf_auth_rate_limit = 100`. 로그인은 요청마다 외부 IdP를 부른다(카카오 2회, 네이버 1회).
backend의 AUTH 제한(IP당 분당 30)은 태스크별 인메모리라 태스크 수만큼 풀리므로, IP를 바꿔 가며 무효
토큰을 보내면 우리 앱의 IdP 호출 쿼터가 소진돼 모든 사용자의 소셜 로그인이 502가 된다. 10번 규칙과 달리
임계값을 backend 태스크 하나의 한도(5분 150)보다 낮게 둬 엣지에서 먼저 자른다. 정상 사용자는 5분에
로그인 1회 + refresh 몇 회라 공유 Wi-Fi 수십 명도 안에 들어온다.

### 관리자 경로 rate 규칙 (KAN-244)

`waf_admin_rate_limit = 50`. 관리자 API는 공개 CloudFront로 열려 있고 공유 토큰 한 겹이다.
IP 허용 목록은 쓰지 않는다. E2E 스모크가 GitHub 호스티드 러너에서 관리자 API를 부르는데 러너 IP가
고정되지 않는다. 대신 추측 속도를 IP당으로 묶고, backend는 인증 실패를 `관리자 인증 실패 ip=...`
WARN으로 남긴다(토큰 값은 남기지 않는다). 활성 버전 전환 감사 행에는 `caller_ip`가 남는다. 스모크의
관리자 호출은 실행당 2건이다. 실패 WARN에 낯선 IP가 반복되면 [`secrets-ssm.md`](secrets-ssm.md)의 "시크릿 회전 (KAN-242)" 절대로 토큰을 회전한다.

```
# 서울 리전 로그 그룹 /accentury/<env>/backend
fields @timestamp, @message
| filter @message like /관리자 인증 실패/
| parse @message /ip=(?<ip>\S+)/
| stats count() by ip
| sort count desc
```

### IP 평판 규칙 (KAN-244)

지금 두 환경 모두 `waf_ip_reputation_enforce = false`라 이 규칙만 Count다. 2026-10-29에 앱 출시 뒤 prod의 Count 로그를 보고 전환하기로 정했다. 처음 계획(2026-10-02, staging 일주일 관찰 뒤 staging부터 true)은 이 결정으로 대체됐다.

다른 규칙이 이미 Block인 뒤에 들어온 규칙이라 혼자 Count 관찰을 거친다. 관찰은 위 "Count 관찰 후 Block 전환" 3번의 WAF 로그
그룹에서 `@message like /aws-ip-reputation/`으로 걸러 IP와 경로를 본다. 그룹 안 규칙 이름은
`AWSManagedIPReputationList`, `AWSManagedReconnaissanceList`, `AWSManagedIPDDoSList`다. 공용 VPN과 클라우드
대역의 정상 사용자가 섞여 있으면 오탐이다. 오탐 기록을 KAN-244에 남긴 뒤 true로 apply한다. apply는 staging부터 하고 prod가 뒤따른다.

### rate limit 임계값의 근거

지금 값은 400이다 (2026-10-30, KAN-299). 2026-08-28에 5문항 기준으로 300을 산정했고, 그 뒤 후기
(`…/feedback`)가 더해졌지만 사용자당 1건이라 두지 않았다. 레벨테스트가 7문항(음성 3 + 단어 4, KAN-260)이
되면서 음성 업로드는 줄었고, 억양 학습 업로드(`…/recordings`)가 더해지면서 학습 몫 100을 올렸다.

- 레벨테스트 1명의 5분 창: 세션 1 + 음성 3문항(재녹음 포함 6건 이내) + 후기 1 = 최대 8건.
- 억양 학습 1명의 5분 창: 카드 1장에 듣기 + 녹음 + 채점 대기(약 10초)로 20~60초라 5~15건. 재녹음과
  실패 재시도(앱은 무제한)가 겹치면 더 늘 수 있고, 상한은 backend의 계정당 분당 20(5분 100)이다.
  이 수치는 화면 흐름 추정이고 실측은 아직 없다.
- 억양 학습 업로드를 같은 규칙에 세는 이유: 건마다 AI 추론을 쓰는 같은 비용 모양이고, 계정 토큰
  검사가 multipart 파싱 뒤(컨트롤러 인자 해석)에 돌아 미인증 홍수도 backend가 본문을 받아 버린다.
  별도 규칙으로 나누지 않고 임계값을 올리는 쪽을 골랐다 (2026-10-30 결정) - 규칙 수와 변수를 늘리지
  않는 대신, 같은 IP의 학습 홍수가 레벨테스트 몫을 깎을 수 있다는 점은 받아들였다.
- backend가 먼저 잡는 층: 레벨테스트 업로드와 세션 생성은 IP당 분당 30(5분 150), 억양 학습 업로드는
  IP당 분당 60(5분 300, `UploadRateLimitFilter`, KAN-299)이라 보통의 초과는 backend가 먼저 잡아 정식
  429 봉투를 낸다. backend 한도는 태스크별이라 태스크 수만큼 풀리고, WAF는 400에서 태스크 수와 무관하게
  홍수를 엣지에서 자른다.
- 공유 Wi-Fi(학교, 시연장)는 외부 IP가 하나다. 레벨테스트와 학습을 동시에 하는 20명 기준, 보통
  20명 x (8 + 10) = 360 < 400. 전원이 재녹음을 다 쓰고 학습도 빠르게 넘기는 최악 20명 x (8 + 15) = 460은
  넘는다. 시연 인원이 20명을 넘거나 학습 위주 시연이면 당일 tfvars 값을 올려 apply한다.
- staging도 같은 값이다. staging에서 본 결과가 prod에 그대로 적용되어야 Count 관찰의
  의미가 있다.

### 비용

환경당 월 약 12달러(웹 ACL 5 + 규칙 7개 x 1) + 요청 100만 건당 0.60달러. 로그는 매치된
요청(음성 업로드의 COUNT 포함)만 7일 보존이라 1달러 미만. 두 환경 합산 월 24달러 안팎이다.

## 전송 구간과 엣지 보안 (KAN-245)

보안 검토(KAN-238) #8, #9, #11, #14, #15를 한 번에 반영한 설정 묶음이다.

| 항목 | 어디 | 내용 |
| --- | --- | --- |
| S3 TLS 강제 | `modules/edge`(web), `modules/ai-host`(boot), bootstrap(tfstate는 `main.tf`, audit는 `audit.tf`, 음성 버킷 voice는 `voice.tf`), `envs/prod/main.tf`(번역 기록 버킷 `accentury-translator-prompt-<account_id>`, prod만) | `aws:SecureTransport = false` 요청 전부 거부 |
| S3 TLS 강제 (관리 밖) | 옛 staging 학습 버킷 `accentury-staging-training-<account_id>`. Terraform 관리 밖이고 KAN-239의 버킷 정책이 실물로 남아 있다 | 같음 |
| 보안 응답 헤더 | `modules/edge`의 `aws_cloudfront_response_headers_policy.security`, 세 동작 모두 | HSTS 1년 + includeSubDomains(preload 없음), nosniff, `X-Frame-Options: DENY`, `strict-origin-when-cross-origin`, CSP는 Report-Only. 수집 지점(`report-uri`/`report-to`)이 아직 없어 위반은 브라우저 콘솔에만 찍힌다. enforce 전환 후속은 수집 엔드포인트부터다. |
| JDBC 서버 인증서 검증 | `modules/config`의 `SPRING_DATASOURCE_URL`, `backend/Dockerfile` | `sslmode=verify-full` + 이미지 안 RDS 전역 CA 번들. 서버 쪽 `rds.force_ssl`은 PG16 기본 파라미터 그룹에서 이미 1 |
| backend 아웃바운드 | `modules/network` | 443(IPv4, IPv6)과 SG 참조 3개(RDS 5432, Redis 6379, AI ALB 8000)만 |
| 계정 감사, 위협 탐지 | `bootstrap/audit.tf` | CloudTrail 관리 이벤트 + 음성, tfstate 버킷 S3 데이터 이벤트, GuardDuty + S3 보호 |

### JDBC 서버 인증서 검증의 적용 순서

`sslrootcert`가 가리키는 CA 번들은 backend 이미지 안에 있다. SSM의 URL이 먼저 바뀌고 옛 이미지 태스크가 뜨면
파일이 없어 DB 연결이 실패하고 기동하지 못한다. 그래서 환경을 새로 짓거나 이 설정을 다시 걸 때는 환경마다 다음 순서를 지킨다.

1. 이 변경이 든 이미지가 먼저 배포된다 (staging은 Dev push, prod는 Release 승격). URL이 그대로라 이 이미지는 옛
   방식으로 붙는다.
2. 그 뒤 `terraform apply`로 URL을 바꾸고, Actions "Image Deploy"를 같은 태그로 다시 돌려 태스크가 새 URL을
   읽게 한다 (secrets는 태스크 시작 때 한 번 읽힌다).
3. 이 apply 뒤로는 KAN-245 이전 이미지로 롤백하지 않는다. 꼭 돌아가야 하면 URL에서 두 파라미터를 먼저 뺀다.

검증이 실제로 켜졌는지는 `sslrootcert`를 없는 경로로 바꾼 태스크가 기동에 실패하는 것으로 확인한다.
CA 번들은 Dockerfile의 `ADD --checksum`이 해시를 고정한다. AWS가 번들을 갱신하면 빌드가 실패하므로 새 해시로 고친다.

### backend 아웃바운드 규칙의 적용 순서

넓은 규칙과 좁은 규칙은 별개 리소스라 한 번의 apply에서 삭제와 생성이 동시에 돈다.
삭제가 먼저 끝나면 그 사이 태스크의 바깥 연결이 끊긴다. 살아 있는 SG에서 넓은 규칙을 지우거나 규칙을 바꿀 때는 좁은 규칙부터 만든다 (KAN-165의 SG 교체 교훈).

```
terraform apply \
  -target=module.network.aws_vpc_security_group_egress_rule.backend_https_ipv4 \
  -target=module.network.aws_vpc_security_group_egress_rule.backend_https_ipv6 \
  -target=module.network.aws_vpc_security_group_egress_rule.backend_to_rds \
  -target=module.network.aws_vpc_security_group_egress_rule.backend_to_redis \
  -target=module.network.aws_vpc_security_group_egress_rule.backend_to_ai_alb
terraform apply    # 넓은 규칙 삭제와 나머지
```

VPC DNS, ECS 태스크 메타데이터, Time Sync는 SG가 거르지 않으므로 규칙이 필요 없다 (AWS VPC 문서). 새 바깥
연결(443이 아닌 포트)을 쓰는 기능을 더하면 여기에 규칙을 함께 더해야 한다. 빠뜨리면 그 연결만 타임아웃이 난다.

### CloudTrail과 GuardDuty

trail은 계정에 하나다(`accentury-account-audit`, 전 리전, 로그 파일 검증). 관리 이벤트는 읽기와 쓰기를 모두
남겨 SSM `GetParameter`, Secrets Manager `GetSecretValue`도 기록된다. S3 데이터 이벤트는 음성 버킷(`accentury-voice-<account_id>`, KAN-269)과 tfstate
버킷 객체만 남긴다. 옛 staging 학습 버킷(`accentury-staging-training-<account_id>`)도 사람이 정리할 때까지 대상에 남긴다. 로그는 `accentury-cloudtrail-<account_id>`에 1년 보관한다.

```
# 최근 음성 버킷 객체 읽기 (CloudTrail 콘솔 이벤트 기록은 데이터 이벤트를 보여 주지 않는다 - 버킷의 로그를 본다)
aws s3 ls s3://accentury-cloudtrail-<account_id>/AWSLogs/<account_id>/CloudTrail/ap-northeast-2/ --recursive | tail
# 관리 이벤트 (90일)
aws cloudtrail lookup-events --lookup-attributes AttributeKey=EventName,AttributeValue=GetParameter --max-results 5
```

GuardDuty는 서울 리전 detector 하나다. 켜 둔 보호는 S3 데이터 이벤트와 RDS 로그인 이벤트뿐이고, AWS가 기본으로 켜는
EKS 감사 로그, Lambda 네트워크 로그, EBS 악성코드 검사는 `audit.tf`가 끈다. 발견 사항은 콘솔 GuardDuty에서 본다.
