# SSM 파라미터와 시크릿

backend와 ai 컨테이너의 환경 변수(SSM Parameter Store `/accentury/{env}/`), 시크릿의 생성과 회전, 재구축 뒤 사람이 넣어야 하는 값, 음성 전용 S3 스위치를 다룬다.

관련 코드:

- [`infra/modules/config/`](../../infra/modules/config) - backend와 ai용 SSM 파라미터 전부 (`main.tf`, 출력 `parameter_names`, `ai_parameter_names`)
- [`infra/modules/fargate/main.tf`](../../infra/modules/fargate/main.tf) - 태스크 정의 secrets, 실행 역할 허용 목록, 태스크 역할의 S3 PutObject
- [`infra/modules/ai-host/main.tf`](../../infra/modules/ai-host/main.tf) - ai 호스트 역할의 SSM 읽기, precondition
- [`infra/modules/data/`](../../infra/modules/data) - ElastiCache Redis와 AUTH 토큰
- [`infra/bootstrap/voice.tf`](../../infra/bootstrap/voice.tf), [`infra/bootstrap/audit.tf`](../../infra/bootstrap/audit.tf) - 음성 전용 버킷, CloudTrail 데이터 이벤트
- [`.github/workflows/e2e-smoke.yml`](../../.github/workflows/e2e-smoke.yml), [`scripts/e2e_smoke.py`](../../scripts/e2e_smoke.py) - 원격 스모크
- `backend/src/main/java/app/accentury/backend/common/DeploymentConfigGuard.java` - 기동 가드, `SsmEnvironmentBindingTest`가 config 모듈과 이름을 대조한다
- `backend/src/main/resources/application-deploy.yml` - JDBC wrapper, Redis TLS

실증 기록은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-242, KAN-171, KAN-269 항목, 결정 경위는 [docs/wiki/infra-decisions.md](../wiki/infra-decisions.md)에 있다.

## 원칙

- backend 환경 변수는 전부 Terraform `modules/config`가 만든다. 값이 다른 모듈의 출력(RDS 주소, 시크릿 ARN, VPC CIDR,
  도메인)이라 손으로 넣으면 재구축 때 어긋난다.
- 파라미터 이름의 마지막 조각이 그대로 컨테이너 환경 변수 이름이다. Spring relaxed binding 규칙대로 점은 밑줄, 대시는
  제거, 대문자다 (`accentury.trusted-proxies` -> `ACCENTURY_TRUSTEDPROXIES`).
- fargate 모듈이 config의 `parameter_names`를 그대로 태스크 정의 secrets와 실행 역할의 허용 목록으로 쓴다. 파라미터를
  더하면 태스크 정의와 역할에 같이 반영되고 코드 변경은 config 모듈 한 곳이다.
- ai-host 모듈은 `ai_parameter_names`를 precondition으로 참조해(`ai/ACCENTURY_AI_INTERNAL_TOKEN`이 있어야 한다) 첫 부팅의
  기동 스크립트가 SSM을 읽는 시점에 파라미터가 이미 있게 한다.
- secrets는 태스크 시작 시 한 번 읽힌다. **값을 바꾼 뒤에는 backend 태스크를 새로 띄운다**
  (`aws ecs update-service --cluster accentury-{env} --service backend --force-new-deployment`).
- 시크릿 값은 레포, 노션, 지라, 채팅, 스크린샷 어디에도 적지 않는다.

## 파라미터 전체 목록

backend 몫은 staging 28개, prod 29개다 (`modules/config/outputs.tf`의 `parameter_names`). 고정 26개에 음성 저장 2개(두
환경 `training_bucket_enabled = true`)가 붙고, prod는 번역 기록 버킷 1개(`translation_records_enabled = true`)가 더 붙는다.
여기에 파이프라인이 쓰는 `IMAGE_TAG`와 ai 호스트 전용 하위 경로 2개가 따로 있다.

"가드"는 `deploy` 프로파일에서 값이 없으면 backend가 기동을 세우는 파라미터다 (`DeploymentConfigGuard`). 아래 표에서
16개다. 자리 표시 값도 "값이 있다"로 통과한다.

| `/accentury/{env}/` 아래 | 값 (출처) | 타입 | 가드 | 읽는 쪽, 비고 |
| --- | --- | --- | --- | --- |
| `IMAGE_TAG` | 배포한 SHA 태그 (배포 파이프라인 KAN-128이 쓴다, Terraform 밖) | String | - | ai 호스트 compose.env, backend 태스크 정의 image (Terraform data 소스). **없으면 ai 기동 실패, plan 실패.** |
| `ai/ACCENTURY_AI_INTERNAL_TOKEN` | `ACCENTURY_ANALYSIS_AITOKEN`과 같은 난수 (ephemeral, write-only) | SecureString | - | ai 호스트만. ai 서버가 health를 뺀 모든 요청에서 대조한다 (KAN-36). |
| `ai/ACCENTURY_AI_ANALYSIS_TIMEOUT_SECONDS` | `75` (config 변수) | String | - | ai 호스트만. AI 자신의 분석 상한 ([ai-host.md](ai-host.md#분석-시간-예산-kan-172-확정)). |
| `SPRING_PROFILES_ACTIVE` | `deploy` (두 환경 동일) | String | 스위치 | 이 프로파일에서만 가드가 돈다. |
| `SPRING_DATASOURCE_URL` | `jdbc:aws-wrapper:postgresql://<RDS 주소>/accentury?secretsManagerSecretId=<마스터 시크릿 ARN>&sslmode=verify-full&sslrootcert=/app/certs/rds-global-bundle.pem` | String | 필수 | 사용자 이름과 비밀번호는 URL에 시크릿 ID가 있으면 비어 있어도 된다 (아래 "DB 비밀번호 파라미터는 없다"). |
| `ACCENTURY_ANALYSIS_AIBASEURL` | `http://ai.accentury.internal:8000` (내부 ALB alias, 두 환경 동일) | String | 필수 | |
| `ACCENTURY_ANALYSIS_AITOKEN` | ephemeral 난수 48자 영숫자, write-only (KAN-242) | SecureString | 필수 | backend가 AI 호출마다 `X-Accentury-Internal-Token`으로 싣는다. |
| `ACCENTURY_ANALYSIS_AITIMEOUT` | `85s` | String | - | 분석 시간 예산. |
| `ACCENTURY_ANALYSIS_PROCESSINGTIMEOUT` | `300s` | String | - | 분석 시간 예산. |
| `ACCENTURY_ANALYSIS_DISPATCHCONCURRENCY` | `3` | String | - | 분석 시간 예산. |
| `ACCENTURY_TRUSTEDPROXIES` | 해당 환경 VPC CIDR 하나 | String | 필수 | |
| `ACCENTURY_RESULT_WEBTESTURL` | `https://<도메인>/t?c=kko_share` | String | 필수 | |
| `ACCENTURY_RESULT_ASSETBASEURL` | `https://<도메인>/share` (KAN-132) | String | 필수 | backend가 등급 code를 붙여 `share.imageUrl`을 만든다. 이미지는 웹 버킷 `share/<code>.png` (`scripts/publish-share-assets.sh`). |
| `ACCENTURY_ADMIN_TOKEN` | ephemeral 난수 48자 영숫자, write-only (KAN-242) | SecureString | 필수 | 관리자 API(§6)와 E2E 스모크(KAN-138). |
| `ACCENTURY_SHARE_KAKAOADMINKEY` | 카카오 앱 Admin 키. 자리 표시 값으로 만들고 apply 뒤 넣는다 (KAN-164) | SecureString | 필수 | 두 환경 같은 값. 아래 "재구축 뒤 넣는 시크릿". |
| `ACCENTURY_FEEDBACK_SLACKWEBHOOKURL` | `#feedback` 채널 Incoming Webhook URL. 자리 표시 값으로 만들고 apply 뒤 넣는다 (KAN-211) | SecureString | 선택 | 없거나 자리 표시 값이면 알림만 끈다. 두 환경 같은 값. |
| `ACCENTURY_AUTH_JWTSECRET` | ephemeral 난수 64자 영숫자, write-only (KAN-223, KAN-242) | SecureString | 필수 | Access JWT(HS256) 서명 키. |
| `SPRING_DATA_REDIS_HOST` | data 모듈 출력 (ElastiCache 주소) | String | 필수 | |
| `SPRING_DATA_REDIS_PASSWORD` | data 모듈의 AUTH 토큰 (`random_password`, state에 남는다) | SecureString | 필수 | 아래 "Redis AUTH 토큰은 state에 남는다". |
| `ACCENTURY_AUTH_GOOGLECLIENTID` | tfvars `auth_google_client_id` (서버용 웹 OAuth 클라이언트 ID) | String | 필수 | |
| `ACCENTURY_AUTH_APPLEBUNDLEID` | tfvars `auth_apple_bundle_id` | String | 필수 | |
| `ACCENTURY_AUTH_KAKAOAPPID` | tfvars `auth_kakao_app_id` (숫자 앱 ID) | String | 필수 | |
| `ACCENTURY_AUTH_NAVERCLIENTID` | tfvars `auth_naver_client_id` (KAN-243) | String | 필수 | |
| `ACCENTURY_AUTH_NAVERCLIENTSECRET` | 네이버 Client Secret. 자리 표시 값으로 만들고 apply 뒤 넣는다 (KAN-243) | SecureString | 필수 | |
| `ACCENTURY_AUTH_APPLETEAMID` | tfvars `auth_apple_team_id` (KAN-241) | String | 선택 | |
| `ACCENTURY_AUTH_APPLEKEYID` | tfvars `auth_apple_key_id` (KAN-241) | String | 선택 | |
| `ACCENTURY_AUTH_APPLEPRIVATEKEY` | Sign in with Apple 키 .p8 원문. 자리 표시 값으로 만들고 apply 뒤 넣는다 (KAN-241) | SecureString | 선택 | 하나라도 자리 표시 값이면 탈퇴 때 revoke만 건너뛴다. |
| `ACCENTURY_TRANSLATION_APIKEY` | Gemini API 키. 자리 표시 값으로 만들고 apply 뒤 넣는다 (KAN-266) | SecureString | 선택 | 환경마다 다른 값. |
| `ACCENTURY_TRANSLATION_MODEL` | tfvars `translation_model` (기본 `gemini-3.5-flash-lite`) | String | - | 없으면 `application.yml` 기본값. |
| `ACCENTURY_TRANSLATION_RECORDBUCKET` | `accentury-translator-prompt-<계정 ID>` | String | - | prod만. 없으면 번역 기록 코드가 돌지 않는다. |
| `ACCENTURY_TRAINING_BUCKET` | `accentury-voice-<계정 ID>` (두 환경 같은 값, KAN-269) | String | - | `training_bucket_enabled`가 true인 환경만(지금 두 환경). 없으면 backend가 S3 클라이언트를 만들지 않는다. |
| `ACCENTURY_TRAINING_KEYPREFIX` | 환경 이름(`staging`, `prod`, 끝 슬래시 없음) | String | - | 위와 함께 생기고 사라진다. 태스크 역할의 PutObject가 같은 접두사 아래로만 열려 있어 값이 다르면 AccessDenied다. |

## 읽기 권한

| 주체 | 읽을 수 있는 것 |
| --- | --- |
| backend 실행 역할 | 자기 환경 `parameter_names`(staging 28, prod 29)의 `ssm:GetParameters`만. `/ai` 하위 경로는 못 읽는다 |
| backend 태스크 역할 | 자기 환경 RDS 마스터 시크릿 1개의 `GetSecretValue` (SSM 없음) |
| ai 호스트 역할 | 인라인 정책은 `/accentury/{env}/ai/*`와 `IMAGE_TAG`만. 다만 `AmazonSSMManagedInstanceCore`가 `ssm:GetParameter`, `ssm:GetParameters`를 `*`에 허용하므로 이름을 아는 다른 파라미터도 단건으로 읽힌다. 경계를 세우려면 ai-ami 빌드 역할처럼 명시 Deny를 더해야 한다 (별도 티켓) |

SecureString은 AWS 관리 키(`aws/ssm`)라 별도 kms 권한이 필요 없다.

## 값 형식 제약

값에 탭이나 개행은 넣을 수 없다 (ai 호스트의 `--output text` 파싱 단위). 그 외 문자(`$`, 따옴표, `#`, 공백)는 그대로
컨테이너에 들어간다 - ai 호스트는 env_file을 `format: raw`로 읽어 Compose의 보간과 따옴표 처리를 끄고, ECS secrets는 값을
그대로 env로 준다. Fargate 태스크는 호스트가 없어 디스크 사본도 없다. 레포, 이미지, 로그에 값이 없다는 것이 KAN-122/129의
요구이고 그것은 충족한다.

## DB 비밀번호 파라미터는 없다

RDS 관리형 마스터 시크릿은 7일마다 자동 회전되므로(일정 변경만 가능) 값을 SSM에 복사하면 첫 회전에서 접속이 끊긴다.
대신 backend가 AWS Advanced JDBC Wrapper의 `awsSecretsManager` 플러그인으로 연결 시점에 Secrets Manager를 직접 읽고, 회전
뒤 인증 실패가 나면 시크릿을 다시 받아 재접속한다 (`application-deploy.yml`). 비밀번호는 SSM, state, 태스크 정의 어디에도
없다. **`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 파라미터를 만들지 않는다.**

## 관리자 토큰

값 읽기 (스모크 실행, 관리자 API 호출용):

```
aws ssm get-parameter --with-decryption --name /accentury/staging/ACCENTURY_ADMIN_TOKEN \
  --query Parameter.Value --output text
```

재발급은 아래 "시크릿 회전"이다. 값은 SSM에만 있다 - Terraform state, 레포, 이미지, 로그 어디에도 없다 (KAN-242).

**이 토큰은 GitHub에 두지 않는다.** 저장소 시크릿은 하나뿐이라 두 환경 토큰을 동시에 담을 수 없고, 아무 브랜치의
워크플로나 꺼내 쓸 수 있어 prod 관리자 토큰을 두기에 맞지 않는다. 파이프라인과 수동 실행이 모두 대상 환경 SSM에서 직접
읽으므로 토큰이 바뀌어도 GitHub 쪽에 맞춰 줄 것이 없다. 예전 안내를 따라 저장소 시크릿 `ACCENTURY_ADMIN_TOKEN`을 넣어 둔
저장소라면 **지워야** 노출이 실제로 사라진다 (워크플로가 안 읽을 뿐 다른 브랜치의 워크플로는 여전히 꺼낼 수 있다).

```
gh secret list                                 # ACCENTURY_ADMIN_TOKEN이 보이면
gh secret delete ACCENTURY_ADMIN_TOKEN         # 지운다
```

### 원격 스모크 수동 실행 (KAN-138)

이미 떠 있는 환경을 도메인 경유로 두드린다. 배포 파이프라인이 두 환경 모두 반영 직후 같은 스모크를 돌리므로(KAN-217)
승격 절차의 일부는 아니고, 배포와 무관하게 한 바퀴 확인하고 싶을 때(경보 확인, 장애 뒤 점검, AMI 교체 뒤, `--voice-wav`
검산 전 정상 흐름 확인) 쓴다. 토큰은 job이 SSM에서, 대상 주소는 그 environment의 변수 `APP_DOMAIN`에서 읽으므로 넣을
입력이 환경 이름뿐이다.

```
gh workflow run e2e-smoke.yml --ref Dev     -f environment=staging
gh workflow run e2e-smoke.yml --ref Release -f environment=prod
```

**주소를 입력으로 받지 않는다.** 사람이 주소를 넣게 두면 SSM에서 막 읽은 관리자 토큰이 `X-Admin-Token`에 실려 임의의
호스트로 나간다. 주소를 직접 정하려면 `workflow_call`로 부르면서 `admin-token`을 함께 넘겨야 하고, 그때는 SSM을 읽지 않는다.
어느 쪽이든 호출자 job에 `permissions: id-token: write`가 있어야 한다 - 재사용 워크플로의 권한은 호출자 권한이 상한이다.

`--ref`가 환경마다 다른 것은 job이 GitHub environment를 지정하기 때문이다. OIDC 역할의 신뢰 정책이 sub를
`repo:OWNER/REPO:environment:{env}`로 묶으므로 environment 지정이 필수이고, 그 environment의 배포 브랜치 정책(staging = Dev,
prod = Release)이 함께 적용된다. 브랜치를 틀리면 "Branch is not allowed to deploy to ..."로 막힌다. prod에는 required
reviewers가 걸려 있어 실행할 때마다 승인을 한 번 받는다.

스모크가 올리는 것은 합성 사인파라 실모델은 그것을 내용 게이트에서 판정 실패로 끊는다. 그래서 기본 실행이 확인하는
종점은 결과가 아니라 **완료 409 `RESULT_RETAKE_REQUIRED`**다 (업로드 -> AI -> BE 상태 기계까지가 실제로 이어졌다는 뜻이다).
결과 검산과 등급, 공유 카드까지 보려면 그 문항들의 대본을 읽은 실제 녹음을 `--voice-wav`로 주고 스크립트를 직접 돌린다
(`scripts/e2e_smoke.py` 머리말).

## 시크릿 회전 (KAN-242)

관리자 토큰, 내부 호출 토큰, JWT 서명 키는 Terraform state에 값을 남기지 않는다. 난수는 `ephemeral "random_password"`가
apply 동안에만 만들고, SSM 파라미터에는 write-only 인자(`value_wo`)로 넘긴다. state에는 `has_value_wo = true`와
`value_wo_version`만 남는다. Terraform이 값을 기억하지 않으므로 회전은 `modules/config/main.tf` 상단 `locals`의 버전
숫자를 올리는 것이다.

| 시크릿 | 버전 local | 다시 읽어야 하는 쪽 |
| --- | --- | --- |
| 관리자 토큰 | `admin_token_version` | backend (워크플로는 SSM에서 직접 읽으므로 GitHub에 맞출 것이 없다) |
| 내부 호출 토큰 (파라미터 2개) | `ai_internal_token_version` | ai 호스트, backend |
| JWT 서명 키 | `jwt_secret_version` | backend |

순서 (환경마다, **staging 먼저**):

1. 버전 숫자를 올리고 `terraform apply`. plan에는 해당 파라미터의 `value_wo_version` 갱신만 나와야 한다. 내부 호출
   토큰을 바꿨다면 apply 직후 두 파라미터가 같은지 대조한다 (값 대신 해시만 출력한다).

   ```
   for n in ACCENTURY_ANALYSIS_AITOKEN ai/ACCENTURY_AI_INTERNAL_TOKEN; do
     aws ssm get-parameter --with-decryption --name "/accentury/staging/$n" --query Parameter.Value --output text | shasum -a 256
   done
   ```

   두 해시가 다르면 apply가 한쪽만 쓰고 실패한 뒤 재시도된 것이다 (재시도의 ephemeral은 새 값이고, 버전이 이미 맞는 쪽은
   다시 쓰이지 않는다). 값이 state에 없어 plan은 이것을 못 본다. `ai_internal_token_version`을 한 번 더 올려 apply하면
   둘이 같은 값으로 다시 쓰인다. **같은 이유로 둘 중 한 파라미터만 `-replace`하지 않는다.**
2. Actions "Image Deploy"를 그 환경으로, `image_tag`에 현재 SSM `IMAGE_TAG` 값을 넣어 수동 실행한다. 같은 태그 재실행은
   ai 호스트를 한 대씩 reload한 뒤 backend에 새 배포를 강제하고(`deploy.yml`, 리비전을 쌓지 않는다) E2E 스모크까지 돈다.
   **ai 먼저, backend 다음**이 내부 호출 토큰에 맞는 순서다 - 그 사이 backend 호출은 401로 끊겨 회로가 열렸다가 닫힌다.
   관리자 토큰과 JWT 키만 바꿨다면 ai reload는 필요 없지만 같은 실행으로 해도 된다.
3. 스모크 통과를 확인한다. JWT 키를 바꿨다면 앱은 다음 요청에서 401을 받고 refresh로 새 Access를 받는다 (그 순간까지
   발급된 Access, 최대 30분이 무효가 된다. Refresh는 Redis라 영향이 없다).

버전 숫자는 두 환경이 같은 모듈에서 읽는다. staging만 apply한 동안 prod plan에는 파라미터 갱신이 남는데, prod도 회전해야
한다는 표시다. 같은 순서로 prod에서 한 번 더 밟는다.

**이 파라미터의 다른 인자를 바꿀 때도 버전을 함께 올린다.** provider는 description 같은 다른 인자가 바뀌어도 PutParameter를
다시 보내는데, 버전이 그대로면 `value_wo`를 읽지 않고 state의 빈 `value`를 실어 거부된다. 자리 표시 값 시크릿 5종도
같은 규칙이다 - `value_wo_version`을 올리면 자리 표시 값이 다시 써지므로 그 뒤 실제 값을 다시 넣어야 한다.

state에 값이 없는지 확인 (envs 루트에서):

```
terraform state pull | jq '.resources[] | select(.module=="module.config" and .type=="aws_ssm_parameter")
  | select(.name|test("admin_token|ai_token|jwt_secret")) | {name, value: .instances[0].attributes.value,
  has_value_wo: .instances[0].attributes.has_value_wo}'
```

`value`가 비어 있고(`""`) `has_value_wo`가 `true`면 된다.

### Redis AUTH 토큰은 state에 남는다 (위험 수용)

aws provider 6.61.0의 `aws_elasticache_replication_group`에는 write-only 인자가 없다. 복제 그룹 자체가 `auth_token`을 state에
두므로 SSM 쪽만 write-only로 바꿔도 의미가 없다. Redis는 사설 서브넷에 있고 redis-sg가 backend-sg의 6379만 받으므로, 토큰을
알아도 VPC 안의 backend 자리에 있지 않으면 쓸 수 없다. provider에 write-only 인자가 생기면 같은 방식으로 옮긴다.

회전은 세 단계다. `ROTATE`는 새 토큰을 더하되 옛 토큰도 계속 받으므로, 유출 대응이라면 **마지막 단계를 빼먹으면 옛 토큰이
살아 있다.**

1. `terraform apply -replace='module.data.random_password.redis_auth_token'` - 새 토큰이 더해지고 옛 토큰도 받는다. 떠 있는
   태스크가 끊기지 않는다.
2. backend 태스크를 새로 띄워(`--force-new-deployment`) 전부 새 토큰을 쓰게 한다.
3. `modules/data`의 `auth_token_update_strategy`를 `"SET"`으로 바꿔 apply하면 옛 토큰이 폐기된다. 그다음 `"ROTATE"`로
   되돌려 다시 apply한다 - 다음 교체가 또 무중단이 되게.

### state 버킷 (bootstrap)

회전해도 옛 값은 이전 state 버전 객체에 남는다. 그래서 state 버킷에 이전 버전 30일 만료 규칙을 두고(그 사이가 잘못된
apply 뒤 복구 창이다), TLS가 아닌 요청을 거부하는 버킷 정책을 둔다. 읽기 주체를 Terraform 운영자 역할로 좁히는 정책은
두지 않는다 - 역할 목록이 틀리면 state가 잠기고, 이 버킷을 읽을 수 있는 주체는 전부 SSM도 직접 읽는 관리자라 좁혀서 얻는
것이 없다 (KAN-242 노출 범위 표).

## 재구축 뒤 넣는 시크릿 5종

외부 콘솔이 발급하는 값이라 Terraform이 만들 수 없는 다섯 개다. `modules/config`는 자리 표시 값
(`unset-put-parameter-after-apply`)으로 파라미터를 만들되 write-only(`value_wo`)라 손으로 넣은 값을 state에 읽어 들이지도,
되돌리지도 않는다. **환경을 새로 지었거나 `value_wo_version`을 올린 뒤에는 다섯 개를 전부 다시 넣는다.**

| 파라미터 | 발급처 | 환경별 값 | 가드 | 자리 표시 값인 동안 |
| --- | --- | --- | --- | --- |
| `ACCENTURY_SHARE_KAKAOADMINKEY` | 카카오디벨로퍼스 [앱 설정] > [앱 키] > **Admin 키** (네이티브 앱 키 아님) | 두 환경 같음 | 필수 | 카카오 공유 웹훅을 전부 401로 거부한다 (`SHARE_WEBHOOK_UNAUTHORIZED`) |
| `ACCENTURY_FEEDBACK_SLACKWEBHOOKURL` | 슬랙 앱 Incoming Webhook (`#feedback`) | 두 환경 같음 | 선택 | 후기 알림만 꺼진다 (`후기 슬랙 알림 꺼짐`) |
| `ACCENTURY_AUTH_NAVERCLIENTSECRET` | 네이버 개발자 센터 앱 설정 (앱의 `NAVER_CLIENT_SECRET`과 같다) | 두 환경 같음 | 필수 | 네이버 로그인만 401 |
| `ACCENTURY_AUTH_APPLEPRIVATEKEY` | 애플 개발자 계정 Keys의 Sign in with Apple 키 .p8 (만들 때 한 번만 내려받는다) | 두 환경 같음 | 선택 | 탈퇴 때 애플 revoke만 건너뛴다 (`애플 토큰 revoke 미설정` WARN) |
| `ACCENTURY_TRANSLATION_APIKEY` | 환경별 Google AI Studio 프로젝트의 API 키 | **환경마다 다름** | 선택 | 번역만 503 `TRANSLATION_UNAVAILABLE` (`번역 API 키가 없다`) |

자리 표시 값도 가드를 통과하므로 다섯 개가 비어 있어도 backend는 뜬다. 가드 필수인 둘(카카오, 네이버)은 파라미터가 **아예
없으면** 기동이 멈추므로 새 필수 파라미터를 더하는 변경은 apply가 이미지 배포보다 먼저다 (아래 각 절).

넣는 법 (환경마다, `<env>`는 staging 또는 prod). 값은 채팅이나 문서에 붙이지 않고 터미널에서 직접 넣는다.

```
aws ssm put-parameter --overwrite --type SecureString --name /accentury/<env>/ACCENTURY_SHARE_KAKAOADMINKEY      --value '<Admin 키>'
aws ssm put-parameter --overwrite --type SecureString --name /accentury/<env>/ACCENTURY_FEEDBACK_SLACKWEBHOOKURL --value '<웹훅 URL>'
aws ssm put-parameter --overwrite --type SecureString --name /accentury/<env>/ACCENTURY_AUTH_NAVERCLIENTSECRET   --value '<Client Secret>'
aws ssm put-parameter --overwrite --type SecureString --name /accentury/<env>/ACCENTURY_AUTH_APPLEPRIVATEKEY     --value file://AuthKey_<Key ID>.p8
aws ssm put-parameter --overwrite --type SecureString --name /accentury/<env>/ACCENTURY_TRANSLATION_APIKEY       --value '<그 환경 프로젝트의 API 키>'
aws ecs update-service --cluster accentury-<env> --service backend --force-new-deployment
```

확인 (backend 기동 로그):

| 파라미터 | 넣은 뒤 보이는 것 |
| --- | --- |
| 카카오 Admin 키 | 아래 "카카오 공유 웹훅 검증 키"의 curl이 200, 카운터가 1 오른다 |
| 슬랙 웹훅 | `후기 슬랙 알림 켜짐 - 채널 라벨 <도메인>` |
| 네이버 Secret | 네이버 로그인 성공 (실기기 앱) |
| 애플 .p8 | `애플 토큰 revoke 미설정` WARN이 사라진다 |
| Gemini 키 | `번역 LLM model=gemini-3.5-flash-lite` |

끄려면 값을 자리 표시 리터럴로 되돌린다 (`--value 'unset-put-parameter-after-apply'`). 슬랙은 알림만, 번역은 번역만 멈춘다.
IdP의 String 값(구글, 애플 번들, 카카오 앱 ID, 네이버 Client ID, 애플 팀 ID와 키 ID)은 시크릿이 아니라 tfvars에 있으므로
재구축 때 따로 넣을 것이 없다.

### 카카오 공유 웹훅 검증 키 (KAN-164)

카카오톡 공유의 "전송 완료"는 카카오가 우리 서버로 보내는 웹훅(`POST /v0/share/kakao/webhook`, API 명세서 §3.8)으로만 셀
수 있고, backend는 그 요청의 `Authorization: KakaoAK {앱 Admin 키}`가 SSM `ACCENTURY_SHARE_KAKAOADMINKEY`와 같을 때만 받는다.
자리 표시 값으로 뜬 backend는 웹훅을 전부 401로 거부한다 - backend가 그 리터럴을 키로 인정하지 않는다
(`KakaoWebhookAuth.PLACEHOLDER`, 카운트가 부풀지 않는다).

**순서: 이 파라미터를 처음 더하는 변경은 두 환경 모두 apply가 먼저, 그 코드를 실은 이미지 배포는 그다음이다.**
`DeploymentConfigGuard`가 필수로 보므로, 태스크 정의에 secret이 들어가기 전에 코드가 배포되면 새 태스크가 기동을 세우고
파이프라인이 롤백한다.

1. 콘솔 [내 애플리케이션] > 앱 > [앱 설정] > [앱 키]에서 **Admin 키**를 읽는다 (네이티브 앱 키는 앱 빌드에 들어가는 공개
   값이라 아니다).
2. 환경마다 값을 넣고 backend 태스크를 새로 띄운다 (위 명령).
3. 콘솔 [앱 설정] > [카카오톡 공유] > [카카오톡 공유 웹훅]에 URL을 **POST**로 등록한다 -
   `https://accentury.app/v0/share/kakao/webhook`. 카카오 앱이 하나라 웹훅 URL도 하나이고, 실제 콜백은 prod로만 온다.
   staging은 아래 curl로 카카오를 흉내 내어 확인한다.
4. 확인 - 같은 `X-Kakao-Resource-ID`를 두 번 보내면 카운터가 1만 오른다.

   ```
   KEY=$(aws ssm get-parameter --with-decryption --name /accentury/staging/ACCENTURY_SHARE_KAKAOADMINKEY --query Parameter.Value --output text)
   curl -sS -o /dev/null -w '%{http_code}\n' -X POST https://<staging 도메인>/v0/share/kakao/webhook \
     -H "Authorization: KakaoAK $KEY" -H 'X-Kakao-Resource-ID: manual-check-1' \
     -H 'Content-Type: application/json' -d '{"CHAT_TYPE":"MemoChat","HASH_CHAT_ID":"x","campaign":"kko_share"}'
   # 폼 인코딩도 같은 결과여야 한다. 카카오가 어느 쪽으로 보내든 받는다.
   curl -sS -o /dev/null -w '%{http_code}\n' -X POST https://<staging 도메인>/v0/share/kakao/webhook \
     -H "Authorization: KakaoAK $KEY" -H 'X-Kakao-Resource-ID: manual-check-2' \
     -H 'Content-Type: application/x-www-form-urlencoded' -d 'CHAT_TYPE=MemoChat&HASH_CHAT_ID=x&campaign=kko_share'
   ADMIN=$(aws ssm get-parameter --with-decryption --name /accentury/staging/ACCENTURY_ADMIN_TOKEN --query Parameter.Value --output text)
   curl -sS "https://<staging 도메인>/admin/v0/analytics" -H "X-Admin-Token: $ADMIN" | jq .shares
   ```

키를 재발급하면(콘솔에서 Admin 키 재발급) 2번을 다시 한다. 웹훅은 앱이 `serverCallbackArgs`에 `campaign=kko_share`를 실어야
온다 - 그 인자가 없는 앱 빌드(KAN-164 이전)에서는 카카오가 콜백을 보내지 않으므로, 실제 전송이 0으로 보이면 앱 버전부터
본다. 앱이 맞는데도 0이면 엣지에서 막혔는지 본다 - WAF의 AWS 관리 규칙은 이 경로에도 Block으로
걸려 있어, 카카오의 요청이 봇 UA나 UA 없음으로 분류되면 backend 로그에 아무 흔적 없이 CloudFront가 403을 낸다. us-east-1의
WAF 로그 그룹에서 `/v0/share/kakao/webhook`의 BLOCK을 찾고, 있으면 그 규칙에 `rule_action_override`(Count)나 scope-down
제외를 더한다.

### 이용 후기 슬랙 알림 (KAN-211)

결과 화면에서 받은 이용 후기(`POST /v0/sessions/{sessionId}/feedback`)는 DB에 저장되고, 저장이 커밋된 뒤 슬랙 채널
`#feedback`으로 한 줄씩 올라간다. 무료 플랜이라 수단이 Incoming Webhook 하나라서 staging과 prod가 채널을 나누지 않고, 구분은
메시지 머리의 환경 라벨(결과 URL의 호스트)이 한다.

**apply와 배포의 순서 제약이 없다.** 이 파라미터는 가드 밖이라 자리 표시 값인 채로 뜬 backend는 알림만 끄고 후기는 그대로
저장한다.

1. 웹훅 URL 받기: api.slack.com/apps > **Create New App** > **From scratch**(앱 이름과 워크스페이스 선택) > 좌측
   **Incoming Webhooks** 켜기 > **Add New Webhook to Workspace** > 채널로 `#feedback` 선택 > 발급된 URL
   (`https://hooks.slack.com/services/...`) 복사.
2. 두 환경에 같은 값을 넣고 backend 태스크를 새로 띄운다 (위 명령).
3. 확인 - backend 로그에 `후기 슬랙 알림 켜짐 - 채널 라벨 <도메인>`이 뜨는지 보고, staging 웹에서 테스트를 한 번 완주해
   후기를 1건 보낸 뒤 채널에 도착하는지 본다. 도착한 메시지에 **회신 이메일 값은 없다** - 유무만 적고 답장할 후기는 세션
   id 앞자리로 DB에서 찾는다.
4. 재발급과 끄기 - 슬랙에서 URL을 다시 발급했으면 2번을 반복한다. 끄려면 자리 표시 리터럴로 되돌린다. 어느 쪽이든 후기
   저장에는 영향이 없다.

**이 URL은 시크릿이다.** 값을 아는 사람은 누구나 그 채널에 아무 메시지나 쓸 수 있으므로(별도 인증이 없다) 레포, 노션, 지라
티켓, 스크린샷 어디에도 적지 않는다. backend 로그도 이 값을 지운다 (`LogMasking`) - 켜짐 로그는 채널 라벨만 찍는다.

## 사투리 텍스트 번역 (KAN-266)

앱의 사투리 번역기(`POST /v0/translations`, 명세서 §3.18)는 backend가 Google Gemini API 무료 등급을 직접 부른다. backend SG는
HTTPS 443을 이미 전체로 열어 두어(`modules/network`의 `backend_https_ipv4`) 아웃바운드 추가는 없다.

| 자원 | 환경 | 내용 |
| --- | --- | --- |
| SSM `ACCENTURY_TRANSLATION_APIKEY` | 둘 다 | SecureString 자리 표시 값(write-only). 값은 apply 뒤에 사람이 넣는다 |
| SSM `ACCENTURY_TRANSLATION_MODEL` | 둘 다 | tfvars `translation_model`(기본 `gemini-3.5-flash-lite`). 품질이 모자라면 이 값만 바꾼다 |
| 버킷 `accentury-translator-prompt-<계정 ID>`와 SSM `ACCENTURY_TRANSLATION_RECORDBUCKET`, 태스크 역할 `translations/` PutObject | prod만 | tfvars `translation_records_enabled = true`. 버전 관리 켬, 만료 없음, `prevent_destroy`, 객체 읽기는 IAM 사용자 accentury-cli, jaeyoung, seongju만 |
| 경보 `accentury-<env>-translation-quota-exhausted` | 둘 다 | `accentury.translation.llm.calls{outcome=rate_limited}`가 5분에 임계(기본 0)를 넘으면 운다 (무료 한도 소진 429) |

**한도.** Gemini 무료 등급 한도는 RPM 15, RPD 500이고 프로젝트 전체 몫이다 (2026-10-15 AI Studio 확인). 한 계정이 다 쓰지
못하게 backend가 성공한 번역을 계정당 하루 5건까지만 받고 6번째부터 LLM 없이 429 `TRANSLATION_DAILY_LIMIT_EXCEEDED`를
돌려준다. 카운터는 Redis 키 `trq:{userId}:{yyyyMMdd}`이고 한국 시각 자정에 바뀐다 (코드 상수 `DAILY_LIMIT`). Redis 장애면
번역도 503 `TRANSLATION_UNAVAILABLE`이다. 분당 한도에 잠깐 닿는 정도를 허용하려면 monitoring 모듈의
`translation_quota_exhausted_threshold`를 올린다.

**apply와 배포의 순서 제약이 없다.** 키는 가드 밖이라 키가 없거나 자리 표시 값인 backend는 번역만 503이고 기동 로그에
`번역 API 키가 없다`가 남는다. 기록 버킷 파라미터가 없는 환경(staging)은 기록 코드가 돌지 않는다.

키는 환경마다 Google AI Studio 프로젝트를 따로 만들어(결제 수단 연결 안 함) 각 프로젝트에서 받는다 - staging 실증이 prod
무료 한도를 쓰지 않게. 넣는 법은 위 "재구축 뒤 넣는 시크릿 5종"이다.

**prod 기록 버킷은 지우지 않는다.** `translation_records_enabled`를 false로 바꾸는 apply는 `prevent_destroy` 때문에 plan이
실패한다 - 그것이 의도다. 기록을 멈추려면 사람이 판단해 state에서 먼저 빼고(버킷과 객체는 남는다) 스위치를 끈다. 같은
이유로 prod 환경 스택의 `terraform destroy`도 이 버킷에서 실패한다. 객체 본문에는 계정 ID가 없고 대체 ID만 있다. 대체
ID와 계정의 대응표는 DB `translation_subject`이고 탈퇴가 그 행을 지운다.

## 앱 계정 인증 - Redis, JWT 키, IdP 값 (KAN-223)

앱(Android, iOS)의 소셜 로그인(명세서 §3.9~§3.13)이 쓰는 인프라다. 웹은 익명이라 아무것도 쓰지 않는다.

| 무엇 | 어디 | 값의 출처 |
| --- | --- | --- |
| Refresh 토큰 저장소 ElastiCache Redis 7.1 (`cache.t4g.micro` 1노드, TLS, 저장 암호화, AUTH 토큰) | `modules/data`, SG는 `modules/network`의 redis-sg(backend-sg의 6379만) | Terraform |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PASSWORD`(SecureString), `ACCENTURY_AUTH_JWTSECRET`(SecureString) | `modules/config` | Terraform (data 모듈 출력, 난수) |
| `ACCENTURY_AUTH_GOOGLECLIENTID`, `ACCENTURY_AUTH_APPLEBUNDLEID`, `ACCENTURY_AUTH_KAKAOAPPID`, `ACCENTURY_AUTH_NAVERCLIENTID`(String) | `modules/config` | tfvars (IdP 콘솔, KAN-224, KAN-243) |
| `ACCENTURY_AUTH_NAVERCLIENTSECRET`(SecureString, write-only) | `modules/config` | apply 뒤 `put-parameter` (KAN-243) |
| `ACCENTURY_AUTH_APPLETEAMID`, `ACCENTURY_AUTH_APPLEKEYID`(String) | `modules/config` | tfvars (KAN-241) |
| `ACCENTURY_AUTH_APPLEPRIVATEKEY`(SecureString, write-only) | `modules/config` | apply 뒤 `put-parameter` (KAN-241) |

**가드 필수 파라미터를 더하는 변경은 apply가 코드 배포보다 먼저다** (KAN-132와 같은 규칙). Redis 2, JWT, IdP 3, 네이버
2가 가드 목록이라, 파라미터 없이 새 이미지가 뜨면 태스크가 기동하지 못하고 롤링 배포가 멈춘다. 환경마다:

1. `terraform apply` - ElastiCache 생성은 다른 자원보다 오래 걸린다(수 분 이상). 태스크 정의의 secrets도 이때 늘어나므로
   backend 서비스가 새 리비전으로 한 번 롤링된다 (옛 이미지는 새 환경 변수를 무시하므로 문제가 없다).
2. 이미지 배포 (KAN-128 파이프라인).
3. 확인 - backend 로그에 기동 실패가 없는지 본다. 가짜 IdP는 배포에서 켤 수 없으므로(`AuthConfig`가 기동을 막는다) 실제
   IdP 토큰이 있어야 로그인 실증이 된다. 그 전에는 `POST /v0/auth/refresh`에 아무 `rt_` 토큰을 보내 401
   `AUTH_REFRESH_INVALID`가 오는지로 Redis 연결을 본다 - 503 `AUTH_STORE_UNAVAILABLE`이면 Redis에 닿지 못한 것이다 (SG,
   TLS, AUTH 토큰 순으로 본다).

**IdP String 값.** 콘솔에서 받기 전까지 자리 표시 값(`unset-put-parameter-after-apply`, 변수 기본값)이고 그동안 그 IdP
로그인만 401 `AUTH_IDP_TOKEN_INVALID`다. 기동과 다른 IdP, 익명 응시는 영향이 없다. 값을 받으면 환경의 `terraform.tfvars`에
적고 apply한 뒤 backend 태스크를 새로 띄운다. 시크릿이 아니라 tfvars에 그대로 적어도 된다. 두 환경 모두 지금 넷 다 들어가
있다. 구글은 **웹 애플리케이션 유형** 클라이언트의 ID다 - iOS, Android 유형 클라이언트 ID로는 aud가 맞지 않는다.

```
auth_google_client_id = "<서버용(웹) OAuth 클라이언트 ID>.apps.googleusercontent.com"
auth_apple_bundle_id  = "<iOS 번들 ID>"
auth_kakao_app_id     = "<카카오 앱 ID(숫자)>"
auth_naver_client_id  = "<네이버 로그인 Client ID>"
```

**네이버는 Client ID와 Secret 둘이다 (KAN-243).** 네이버 사용자 조회 API는 토큰의 발급 앱을 알려 주지 않아서, backend가 앱이
보낸 SDK refresh token을 이 두 값으로 교환해 성공해야 우리 앱의 토큰으로 본다. 두 파라미터 모두 가드 목록이다. 값은
네이버 개발자 센터 앱 설정의 것이고 앱의 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`과 같다. Client ID는 tfvars에, Secret은
apply 뒤 넣는다. 둘 중 하나라도 자리 표시 값인 동안 네이버 로그인만 401이다.

**애플 탈퇴 revoke는 팀 ID, 키 ID, 키(.p8) 셋이다 (KAN-241).** 회원 탈퇴(명세서 §3.14) 때 backend가 앱이 보낸 애플
authorization code를 교환해 애플 토큰을 revoke한다(애플 심사 지침 5.1.1(v)). client_secret이 애플 키로 서명한 JWT라 키
원문이 서버에 있어야 한다. 팀 ID와 키 ID는 tfvars에, 키 원문은 apply 뒤 파일째 넣는다.

```
auth_apple_team_id = "<팀 ID 10자>"
auth_apple_key_id  = "<Key ID 10자>"
```

세 파라미터는 가드 밖이라 순서 제약이 없다. 하나라도 자리 표시 값인 동안 backend는 기동 때 `애플 토큰 revoke 미설정` WARN을
한 번 남기고, 애플 계정의 탈퇴는 revoke 없이 성공한다(탈퇴 때마다 `애플 토큰 revoke 건너뜀` WARN).

**키 재발급.** JWT 서명 키는 위 "시크릿 회전"(`local.jwt_secret_version`), Redis AUTH 토큰은 위 "Redis AUTH 토큰은 state에
남는다"의 세 단계다.

**Redis가 죽거나 교체되면.** 로그인, refresh, 로그아웃, 번역만 503이고 익명 응시와 헬스체크는 멀쩡하다 - Redis는 backend
health 판정에서 뺐다 (`management.health.redis.enabled: false`, **넣으면 ALB가 태스크를 전부 뺀다**). 노드를 새로 만들면
저장된 Refresh가 사라져 앱 사용자가 한 번씩 다시 로그인한다. 그 이상의 손실이 없어 스냅샷을 두지 않는다.

**비용.** `cache.t4g.micro` 1노드, 환경당 1개다 (스냅샷 없음). 월 금액은 apply 전에 AWS 요금표(서울 리전 온디맨드)로
확인한다.

## 음성 전용 S3 (KAN-269)

모델을 다시 학습시킬 실발화 데이터를 모으려고, 음성 저장(선택 동의)에 동의한 세션의 음성 WAV와 AI 원점수 메타 JSON을 S3에
남긴다. 동의하지 않은 세션의 음성은 요청 처리 중에만 메모리에 있고 어디에도 남지 않는다. 두 환경이 버킷 하나를 함께 쓰고
키 접두사로 나뉜다.

| 항목 | 값 |
| --- | --- |
| 버킷 | `accentury-voice-<계정 ID>` 하나. **bootstrap 스택이 만든다** (`bootstrap/voice.tf`). envs는 같은 규칙으로 이름만 조립한다 |
| 환경 구분 | 키 접두사 `staging/`, `prod/`. tfvars `training_bucket_enabled = true`인 환경만 쓴다 (두 환경 모두 true) |
| 보호 | 퍼블릭 액세스 전면 차단, SSE-S3 기본 암호화, 버전 관리 켬, 수명주기(만료) 규칙 없음, `force_destroy` 없음, `prevent_destroy` |
| 버킷 정책 | TLS가 아닌 요청 전부 거부. 객체 본문 읽기(`s3:GetObject`, `s3:GetObjectVersion`)는 `voice_reader_principal_arns`의 주체만 - 기본값은 학습 담당 IAM 사용자 `jaeyoung`, 학습용 EC2 역할 `accentury-track2-ec2-role`, 운영 담당 IAM 사용자 `accentury-cli`. 목록 밖이면 관리자 자격 증명도 본문은 못 읽는다. List, Put, Delete는 정책이 거부하지 않는다 (List를 거부하면 Terraform refresh가 잠긴다) |
| 쓰기 권한 | backend 태스크 역할에 `arn:aws:s3:::accentury-voice-<계정 ID>/<환경>/*`로 한정한 `s3:PutObject`만 (fargate 모듈 `training_bucket_arn`, `training_key_prefix`). staging 태스크는 `prod/` 아래에 쓰지 못한다. Get, List, Delete 없음 |
| 스위치 | SSM `ACCENTURY_TRAINING_BUCKET` -> `accentury.training.bucket`, `ACCENTURY_TRAINING_KEYPREFIX` -> `accentury.training.key-prefix`. 버킷 값이 없으면 backend는 S3 클라이언트도 저장 빈도 만들지 않는다 (`TrainingConfig`) |
| 저장 대상 | 음성까지 남기는 것은 음성 저장에 동의한 세션만. 동의하지 않은 실사용자 세션은 음성 없이 메타 JSON만 남긴다 (익명과 계정 세션 모두, 계정 id와 대응표는 쓰지 않는다). 합성 트래픽 세션(관리자 토큰으로 만드는 배포 스모크)은 아무것도 남기지 않는다. 판정은 backend가 한다 |
| 저장 시점 | 분석 상태 전이가 끝난 뒤, 오디오 버퍼 파기 전 (`HttpAnalysisDispatcher`). 성공과 판정 실패 모두, 계약 위반과 AI 불가는 제외 |
| 키 | `<환경>/<region>/<testVersion>/<sessionId>/<itemId>/<analysisJobId>.wav` 와 `.json` (region은 세션 생성 요청의 출신 지역 코드 10개 중 하나 또는 `UNKNOWN`) |
| 음성 없는 건의 키 | `<환경>/_no-audio/<region>/<testVersion>/<sessionId>/<itemId>/<analysisJobId>.json` 하나 (KAN-274). 음성 트리의 칸이 언제나 WAV와 JSON 한 쌍이도록 접두를 갈랐다. JSON의 `audioStored`가 false이고 동의 버전과 동의 시각 키가 없다 |
| 단어 정오 기록 | 레벨테스트 단어 답안 1건마다 `<환경>/_no-audio/<region>/<testVersion>/<sessionId>/<itemId>/<answerId>.json` 하나 (KAN-276). 동의와 상관없이 실사용자 세션이고 `gn-2026.10.2`와 그 뒤 버전만. 답안 커밋 뒤 전용 실행기로 비동기 저장(`S3VocabAnswerSampleStore`). `itemType`(VOICE, VOCABULARY)으로 가른다 |
| 실패 | 삼킨다 - WARN 로그 1줄 + 지표 `accentury.training.samples`(result=failed). 분석 결과, 상태 조회, 답안 응답에 영향 없음. 성공은 result=saved(음성과 메타), result=label_saved(메타만), result=vocab_saved(단어 정오)로 갈린다 |

**음성은 Terraform이 지우지 않고, 사람도 함부로 지우지 않는다.** 버킷이 환경 스택 밖(bootstrap)에 있어 환경 destroy나 스위치
끄기가 버킷을 건드리지 못하고, bootstrap에서도 `prevent_destroy`라 버킷을 지우는 plan은 실패한다. 만료 규칙이 없어 S3가
알아서 지우는 일도 없다. 버전 관리가 켜져 있어 같은 키를 덮어쓰거나 지워도 이전 버전이 남는다. 음성을 지우는 일은 사람이
판단해서 사람이 한다. **만료 규칙을 더하지 않는다.**

**배포 순서.** 이미지 배포와 환경 apply의 앞뒤는 어느 쪽이든 안전하다. 새 이미지는 두 파라미터가 없으면 저장 빈을 만들지
않고 그대로 뜬다. apply가 먼저이고 접두사를 모르는 옛 이미지가 떠 있는 동안에는 분석마다 AccessDenied WARN과
`accentury.training.samples{result=failed}`가 남는다. 태스크 역할이 자기 접두에만 쓸 수 있어 막히는 것이고 의도한 동작이니,
**역할을 넓혀서 고치지 않는다.** bootstrap이 버킷을 만들기 전에 환경을 먼저 적용하면 저장이 NoSuchBucket으로 실패하므로
새로 지을 때는 bootstrap이 먼저다.

**옛 버킷 `accentury-staging-training-<계정 ID>`.** KAN-269 적용 뒤로는 Terraform이 관리하지 않는다 (envs main.tf의 `removed`
블록, `destroy = false`). 버킷과 그 안의 음성은 그대로 남고 새 객체는 쌓이지 않는다. 버킷 정책(GetObject 제한)과 읽기 역할도
관리에서만 빠졌으므로 읽기 제한은 그대로이고, CloudTrail 데이터 이벤트도 버킷이 정리될 때까지 계속 기록한다
(`bootstrap/audit.tf`). **staging plan에 `aws_s3_bucket.training`이 "will be destroyed"로 보이면 apply하지 않는다.** 이 버킷을
옮기거나 비우거나 지우는 일은 사람만 한다 - Terraform과 자동화는 손대지 않는다.

**방침 버전과 함께 맞출 것.** 음성 저장 동의 문구가 바뀌어 방침 버전을 올리면, backend는 가입 요청의 방침 버전이 서버 값과
정확히 같아야 받으므로(`AuthService`) 옛 상수를 가진 앱 빌드의 신규 가입이 400 `AUTH_CONSENT_REQUIRED`가 된다 (기존 회원
로그인은 영향 없다). prod는 새 상수를 가진 앱 빌드의 스토어 배포(`Accentury_App` 레포), prod `privacy.html` 게시
(`scripts/publish-privacy.sh prod`), backend Release 승격을 같은 때에 맞춘다.

**teardown.** 환경 destroy는 음성 버킷을 지우지 않는다. 그 환경의 두 파라미터와 태스크 역할 문장만 사라지고 `staging/`,
`prod/` 아래 객체는 그대로다.

확인:

```
aws s3 ls "s3://$(terraform output -raw training_bucket)/$(terraform output -raw training_key_prefix)/" --recursive | tail   # 환경/지역/버전/세션/문항/작업.wav|.json
```

객체 본문(`aws s3 cp`)은 `voice_reader_principal_arns`의 주체로만 읽힌다 - 그 밖의 자격 증명은 AccessDenied다.

## 이미 있는 SSM 파라미터 (재구축)

`modules/config`가 만드는 이름이 계정에 이미 있으면 apply가 `ParameterAlreadyExists`로 실패한다 - `aws_ssm_parameter`는
Terraform이 만들지 않은 값을 덮어쓰지 않는다. 손으로 만든 값이 있거나 state를 잃은 채 다시 적용하는 스택이면 같은 이름을
import로 state에 흡수한다. 리소스 주소는 `module.config.aws_ssm_parameter.<이름>`이고 목록은 `modules/config/main.tf`의
리소스 전부다 (고정 26개 + `training_bucket[0]`, `training_key_prefix[0]`, prod는 `translation_record_bucket[0]`).

```
cd infra/envs/staging
for r in spring_profiles_active:SPRING_PROFILES_ACTIVE ai_base_url:ACCENTURY_ANALYSIS_AIBASEURL \
         datasource_url:SPRING_DATASOURCE_URL trusted_proxies:ACCENTURY_TRUSTEDPROXIES \
         web_test_url:ACCENTURY_RESULT_WEBTESTURL asset_base_url:ACCENTURY_RESULT_ASSETBASEURL \
         admin_token:ACCENTURY_ADMIN_TOKEN ai_token_backend:ACCENTURY_ANALYSIS_AITOKEN \
         ai_token_ai:ai/ACCENTURY_AI_INTERNAL_TOKEN ai_analysis_timeout_seconds:ai/ACCENTURY_AI_ANALYSIS_TIMEOUT_SECONDS \
         analysis_ai_timeout:ACCENTURY_ANALYSIS_AITIMEOUT analysis_processing_timeout:ACCENTURY_ANALYSIS_PROCESSINGTIMEOUT \
         analysis_dispatch_concurrency:ACCENTURY_ANALYSIS_DISPATCHCONCURRENCY \
         kakao_admin_key:ACCENTURY_SHARE_KAKAOADMINKEY feedback_slack_webhook_url:ACCENTURY_FEEDBACK_SLACKWEBHOOKURL \
         jwt_secret:ACCENTURY_AUTH_JWTSECRET redis_host:SPRING_DATA_REDIS_HOST redis_password:SPRING_DATA_REDIS_PASSWORD \
         google_client_id:ACCENTURY_AUTH_GOOGLECLIENTID apple_bundle_id:ACCENTURY_AUTH_APPLEBUNDLEID \
         kakao_app_id:ACCENTURY_AUTH_KAKAOAPPID naver_client_id:ACCENTURY_AUTH_NAVERCLIENTID \
         naver_client_secret:ACCENTURY_AUTH_NAVERCLIENTSECRET apple_team_id:ACCENTURY_AUTH_APPLETEAMID \
         apple_key_id:ACCENTURY_AUTH_APPLEKEYID apple_private_key:ACCENTURY_AUTH_APPLEPRIVATEKEY \
         translation_api_key:ACCENTURY_TRANSLATION_APIKEY translation_model:ACCENTURY_TRANSLATION_MODEL \
         'training_bucket[0]':ACCENTURY_TRAINING_BUCKET 'training_key_prefix[0]':ACCENTURY_TRAINING_KEYPREFIX; do
  terraform import "module.config.aws_ssm_parameter.${r%%:*}" "/accentury/staging/${r#*:}"
done
```

import한 뒤의 apply는 값을 코드 값으로 갱신한다. write-only 파라미터는 import한 state에 `value_wo_version`이 없어 첫 apply가
값을 다시 쓸 것으로 본다 (추정, plan으로 확인한다). 그러면 관리자 토큰, 내부 호출 토큰 2개, JWT 키는 새 난수가 되고, 자리
표시 값 시크릿 5종은 자리 표시 값으로 덮이므로 위 "재구축 뒤 넣는 시크릿 5종"을 다시 밟는다.

이 설계 이전 이름(`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 등)은 Terraform이 모르므로 그대로 남는다. backend
태스크 정의는 `parameter_names`만 싣기 때문에 그 값이 컨테이너에 들어가지는 않지만, 혼동을 막으려고 지운다.

```
aws ssm delete-parameters --names /accentury/staging/SPRING_DATASOURCE_USERNAME \
  /accentury/staging/SPRING_DATASOURCE_PASSWORD
```
