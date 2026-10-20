# backend 컨테이너 환경 변수의 정본 - SSM Parameter Store /accentury/{env}/* (KAN-129).
#
# 값이 전부 다른 모듈의 출력(RDS 주소, 마스터 시크릿 ARN, VPC CIDR, 도메인)이거나 두 환경이
# 같은 상수라, 손으로 put-parameter 하면 재구축 때마다 어긋난다. 이름의 마지막 조각이 그대로
# 컨테이너 환경 변수 이름이다 (accentury-up.sh, KAN-124) - Spring relaxed binding 규칙에 따라
# 점은 밑줄로, 대시는 제거, 대문자다 (accentury.trusted-proxies -> ACCENTURY_TRUSTEDPROXIES).
#
# 같은 이름의 파라미터가 계정에 이미 있으면(재구축 전 수동 생성분) apply가 ParameterAlreadyExists로
# 실패한다 - aws_ssm_parameter는 남의 값을 덮어쓰지 않는다. 그때는 README "이미 있는 SSM 파라미터"
# 절대로 import한다.
#
# 여기 없는 것:
#   IMAGE_TAG        - 배포 파이프라인(KAN-128)이 쓴다. Terraform이 소유하면 배포마다 drift다.
#   ACCENTURY_AI_*   - 스텁은 설정 없이 뜬다 (KAN-22 실모델 때 추가). 예외 하나가 아래 내부 호출
#                      토큰인데, 그것은 ai 호스트 전용 하위 경로 {prefix}/ai/ 에 둔다 (KAN-36).
#   DB 비밀번호      - SSM에 두지 않는다. RDS 관리형 시크릿은 7일마다 회전되므로 복사본은 첫
#                      회전에서 죽는다. backend가 URL의 secretsManagerSecretId로 Secrets Manager에서
#                      직접 읽는다 (application-deploy.yml, awsSecretsManager 플러그인).

terraform {
  required_providers {
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

# ---- 배포 공통 상수 (두 환경 같은 값) ----

# 이 프로파일에서만 backend가 필수값 누락 시 기동을 세운다 (DeploymentConfigGuard).
resource "aws_ssm_parameter" "spring_profiles_active" {
  name  = "${var.ssm_prefix}/SPRING_PROFILES_ACTIVE"
  type  = "String"
  value = "deploy"
}

# 전용 EC2의 ai 서비스 (KAN-36 A단계). 값은 인스턴스 주소가 아니라 프라이빗 영역의 이름이다 -
# ASG가 인스턴스를 교체해도 새 인스턴스가 같은 이름의 A 레코드를 갱신하므로 이 값은 안 바뀐다.
# 이름 자체는 network 모듈이 정하고(ai.accentury.internal, 두 환경 같은 값) 여기는 조립만 한다.
# 배포 갱신은 systemctl reload accentury(backend 호스트)로 backend.env에 반영된다.
resource "aws_ssm_parameter" "ai_base_url" {
  name  = "${var.ssm_prefix}/ACCENTURY_ANALYSIS_AIBASEURL"
  type  = "String"
  value = "http://${var.ai_dns_name}:8000"
}

# ---- 환경별 값 ----

# AWS Advanced JDBC Wrapper URL. 사용자 이름과 비밀번호 파라미터는 없다 - 시크릿 ARN만 넘기면
# 플러그인이 연결 시점에 읽고, 회전 뒤 인증 실패가 나면 다시 받아 재접속한다 (KAN-129 확정).
# ARN을 URL 쿼리에 그대로 넣는다 - 쿼리 구분자(&, =)가 ARN에 없어 인코딩이 필요 없다.
#
# sslmode=verify-full (KAN-245): 기본값 prefer는 암호화만 하고 서버 인증서를 검증하지 않아, 경로 중간에서
# 다른 서버가 RDS인 척해도 붙는다. Redis는 이미 검증하므로 그 비대칭을 없앤다. CA 번들은 backend 이미지에
# 들어 있다 (backend/Dockerfile의 /app/certs/rds-global-bundle.pem). 이 두 쿼리 파라미터는 wrapper가 자기
# 것이 아니라서 그대로 pgJDBC에 넘긴다. 서버 쪽 rds.force_ssl은 PG16 기본 파라미터 그룹에서 이미 1이다.
# 순서 주의: 이 값이 CA 번들 없는 옛 이미지에 닿으면 기동이 실패한다 - 새 이미지 배포가 먼저, 이 apply가
# 나중이다. 이 apply 뒤로는 KAN-245 이전 이미지로 롤백하지 않는다 (README "JDBC 서버 인증서 검증").
resource "aws_ssm_parameter" "datasource_url" {
  name  = "${var.ssm_prefix}/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:aws-wrapper:postgresql://${var.rds_endpoint}/${var.db_name}?secretsManagerSecretId=${var.rds_master_user_secret_arn}&sslmode=verify-full&sslrootcert=/app/certs/rds-global-bundle.pem"
}

# 요청 제한의 기준 IP를 정할 때 신뢰하는 프록시 대역 (KAN-28 ClientIps). VPC CIDR 하나다 -
# CloudFront VPC 오리진 ENI와 internal ALB가 둘 다 VPC 안이라 XFF의 오른쪽 두 홉이 이 대역에
# 들고, 그 앞의 값(뷰어 IP)이 사용자다. CloudFront 오리진 페이싱 공인 대역은 VPC 오리진에서
# 매칭될 일이 없어 넣지 않는다 (2026-08-25 정정).
resource "aws_ssm_parameter" "trusted_proxies" {
  name  = "${var.ssm_prefix}/ACCENTURY_TRUSTEDPROXIES"
  type  = "String"
  value = var.vpc_cidr
}

# 공유 카드가 여는 웹 테스트 URL (§3.7, KAN-30) - 캠페인 파라미터까지 붙은 완성 URL.
resource "aws_ssm_parameter" "web_test_url" {
  name  = "${var.ssm_prefix}/ACCENTURY_RESULT_WEBTESTURL"
  type  = "String"
  value = "https://${var.domain}/t?c=kko_share"
}

# 등급 이미지의 기준 URL (§3.7 share.imageUrl, KAN-132). backend가 등급 code로 `{기준}/{code}.png`를
# 만든다 - 값 5개 대신 1개다. 이미지는 웹 S3 버킷의 share/ 아래에 있고(scripts/publish-share-assets.sh)
# CloudFront 기본 동작(S3 오리진)이 서빙한다. 파일명에 확장자가 있어 SPA 재작성 Function에 걸리지
# 않는다. 도메인이 환경마다 달라 여기서 조립한다 - 코드 기본값(prod 도메인)이 staging에 새면 안 된다.
resource "aws_ssm_parameter" "asset_base_url" {
  name  = "${var.ssm_prefix}/ACCENTURY_RESULT_ASSETBASEURL"
  type  = "String"
  value = "https://${var.domain}/share"
}

# ---- 시크릿 ----

# 아래 세 시크릿(관리자 토큰, 내부 호출 토큰, JWT 서명 키)은 Terraform state에 값을 남기지 않는다 (KAN-242).
# 난수는 ephemeral random_password가 apply 동안에만 만들고, 파라미터에는 write-only 인자(value_wo)로
# 넘긴다 - provider는 value_wo를 state에 두지 않고, read 때도 value를 비운 채 has_value_wo만 남긴다.
# random_password 리소스와 value로 두면 결과가 state(버전 관리 버킷)에 평문으로 남아, 버킷을 읽을 수 있는
# 주체는 누구나 JWT를 위조할 수 있었다. 카카오 Admin 키(아래 kakao_admin_key)가 먼저 쓴 방식이다.
#
# ephemeral 값은 plan과 apply마다 새로 뽑히지만, provider는 value_wo_version이 바뀔 때만 그 값을 쓴다.
# 그래서 아래 버전 숫자를 올리는 것이 곧 회전이다 - 올린 뒤 apply, ai 호스트 reload, backend 새 배포,
# E2E 스모크 순서다 (README "시크릿 회전" 절). 두 환경이 같은 모듈을 쓰므로 숫자 하나가 두 환경의 회전이고,
# 한 환경만 apply한 동안 다른 환경 plan에는 파라미터 갱신이 남는다 - 그 환경도 회전해야 한다는 표시다.
#
# 이 파라미터의 다른 인자(description, type 등)를 바꿀 때도 버전을 함께 올린다. provider는 다른 인자가
# 바뀌어도 PutParameter를 다시 보내는데, 버전이 그대로면 value_wo를 읽지 않고 state의 빈 value를 실어
# 거부된다 (SSM 값은 1자 이상).
locals {
  admin_token_version       = 1
  ai_internal_token_version = 1
  jwt_secret_version        = 1
}

# 관리자 API(§6)와 E2E 스모크(KAN-138) 합성 트래픽 표시의 공유 시크릿. AdminAuth가 32자 미만을
# 거부하므로 그 위로 넉넉히 잡는다. 특수문자를 빼는 것은 curl과 워크플로 YAML에서 따옴표 문제를
# 만들지 않기 위해서다 - 48자 영숫자면 엔트로피는 충분하다. 값 조회:
#   aws ssm get-parameter --with-decryption --name /accentury/{env}/ACCENTURY_ADMIN_TOKEN --query Parameter.Value --output text
# 워크플로(배포 스모크, 수동 스모크)도 SSM에서 직접 읽으므로 회전 뒤 GitHub에 맞춰 줄 것이 없다.
ephemeral "random_password" "admin_token" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "admin_token" {
  name             = "${var.ssm_prefix}/ACCENTURY_ADMIN_TOKEN"
  type             = "SecureString" # AWS 관리 키(aws/ssm) - EC2 역할에 별도 kms 권한이 필요 없다 (compute IAM 주석).
  value_wo         = ephemeral.random_password.admin_token.result
  value_wo_version = local.admin_token_version
}

# backend -> ai 내부 호출의 공유 시크릿 (KAN-36). 두 서비스가 다른 호스트로 갈라지면서 "같은
# compose 네트워크라 backend만 부를 수 있다"는 전제가 사라졌으므로, SG 한 겹 뒤에 헤더 검사를 한 겹
# 더 둔다. 난수 하나를 두 이름으로 싣는다 - backend 쪽은 Spring 프로퍼티 이름 규칙
# (accentury.analysis.ai-token -> ACCENTURY_ANALYSIS_AITOKEN), ai 쪽은 FastAPI 설정 이름
# (ACCENTURY_AI_INTERNAL_TOKEN)이고, ai 것은 ai 호스트 역할만 읽는 하위 경로 {prefix}/ai/ 에 둔다
# (compute 모듈 IAM). backend 호스트의 기동 스크립트는 하위 경로를 읽지 않는다 (--recursive 없음).
# 두 파라미터가 같은 버전 local을 쓰는 것이 핵심이다 - 한 apply 안에서 ephemeral 값은 하나라 둘 다 같은
# 값을 받지만, 한쪽 버전만 올리면 그쪽만 새 값이 되어 backend와 ai가 서로 다른 토큰을 갖는다.
# 같은 어긋남이 apply 도중 실패로도 생긴다 (Codex 리뷰 P2) - 한쪽만 쓰인 뒤 재시도하면 재시도의 ephemeral은
# 새 값이고 버전이 이미 맞는 쪽은 다시 쓰이지 않는다. 값이 state에 없으니 plan도 이것을 못 본다. 그래서
# 회전 뒤 두 값을 대조하고, 다르면 버전을 한 번 더 올려 둘을 같은 apply에서 다시 쓴다 (README "시크릿 회전").
# 한 파라미터만 -replace하는 것도 같은 이유로 하지 않는다.
ephemeral "random_password" "ai_internal_token" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "ai_token_backend" {
  name             = "${var.ssm_prefix}/ACCENTURY_ANALYSIS_AITOKEN"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.ai_internal_token.result
  value_wo_version = local.ai_internal_token_version
}

resource "aws_ssm_parameter" "ai_token_ai" {
  name             = "${var.ssm_prefix}/ai/ACCENTURY_AI_INTERNAL_TOKEN"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.ai_internal_token.result
  value_wo_version = local.ai_internal_token_version
}

# 실모델 기준 분석 시간 예산 (KAN-172 확정, 2026-09-08. KAN-22가 임시로 올렸던 값을 KAN-57의
# c7i.xlarge 실측으로 다시 정했다 - bf16 + MFA align_one에서 1건 P50 10.1초, P95 11.1초).
#
# 값은 코드 기본값(application.yml, ai/app/config.py)과 같다. SSM에 두는 이유는 실측이 바뀌었을 때
# 이미지 재빌드 없이 환경별로 조정하기 위해서다.
#
# 값 사이의 관계는 backend가 기동 시점에 강제한다 (AnalysisDispatchConfig).
#
#   processing-timeout > ai-timeout x (재시도 2 + 1) + 백오프 0.9초   -> 300 > 255.9
#   shutdown-budget(90초, 코드 기본값) > ai-timeout                    -> 90 > 85
#   ai_analysis_timeout_seconds < ai-timeout                           -> 75 < 85
#   ai_analysis_timeout_seconds > 한 호스트에 겹친 6건 x 1건 P95 11.1초 -> 75 > 66.6
#   ai_analysis_timeout_seconds > 워커 재적재 31초 + 1건 P95 11.1초     -> 75 > 42.1
#
# AI는 호스트마다 추론을 한 번에 하나만 돌리므로(단일 lock, 8GB에서 2건이면 OOM - KAN-57) 여럿을
# 동시에 보내면 뒤의 것은 앞의 추론이 끝나기를 AI 안에서 기다린다. AI 상한은 그 대기와 워커 재적재
# 대기까지 포함하므로 한 호스트에 6건이 겹친 경우와 워커가 죽은 뒤의 재적재를 덮어야 한다 - 짧으면
# 이미 추론 중인 요청을 끊어 멀쩡한 워커를 죽이고, 재전송이 재적재를 기다리다 또 끊겨 새 워커를 또
# 죽인다 (Codex 리뷰 P1). 값은 KAN-22가 staging 검증용으로 올렸던 임시값과 같다 - 근거가 붙어 정식값이 됐다.
#
# dispatch-concurrency는 3이다 (KAN-272, 2026-10-06. 그 전에는 1). 1이던 때는 AI를 2대로 늘려도 태스크
# 하나가 한 번에 1건만 보내 처리량이 늘지 않았다 (prod 실측 - AI lock 대기 0초, 분당 완료 최대 7건).
# 3은 AI 최대 대수(ai_max_size)와 같은 값이다. 한 호스트에 겹치는 호출 수는 "태스크 수 x 3 / AI 대수"다.
#
#   태스크 1개(평시)        x 3 = 3건  -> AI 1대여도 33초          (상한 안)
#   태스크 2개(롤링 배포 중) x 3 = 6건  -> AI 1대여도 67초          (상한 안, 위 불변식의 근거)
#   태스크 3개(스케일아웃)  x 3 = 9건  -> AI 1대면 100초            (상한 밖. AI가 2대면 50초로 안이다)
#
# 마지막 줄은 backend가 3개로 늘었는데 AI 스케일아웃이 아직 안 끝난 구간에서만 생긴다. 그대로 두면 일곱
# 번째 요청이 66.6초에 lock을 잡아 **추론 도중** 75초 상한에 걸리고, 그 취소가 멀쩡한 워커를 죽여 재적재
# 31초가 뒤 요청까지 민다 (Codex astra 리뷰 P1). 그래서 AI는 lock을 잡았을 때 상한까지 남은 시간이
# 여유분(코드 기본값 15초, ACCENTURY_AI_INFERENCE_RESERVE_SECONDS)보다 적으면 추론을 시작하지 않고
# 429로 돌려준다 (ai/app/track1.py). 추론 전 거절이라 backend는 시도 예산을 깎지 않고 재전송 예산(2회)
# 안에서 다시 보내고, 다 쓰면 ANALYSIS_UNAVAILABLE로 종결해 재업로드를 연다. 15초면 한 호스트에 겹친
# 6건까지는 전부 통과한다 (여섯 번째가 55.5초에 시작한다).
resource "aws_ssm_parameter" "analysis_ai_timeout" {
  name  = "${var.ssm_prefix}/ACCENTURY_ANALYSIS_AITIMEOUT"
  type  = "String"
  value = var.analysis_ai_timeout
}

resource "aws_ssm_parameter" "analysis_processing_timeout" {
  name  = "${var.ssm_prefix}/ACCENTURY_ANALYSIS_PROCESSINGTIMEOUT"
  type  = "String"
  value = var.analysis_processing_timeout
}

resource "aws_ssm_parameter" "analysis_dispatch_concurrency" {
  name  = "${var.ssm_prefix}/ACCENTURY_ANALYSIS_DISPATCHCONCURRENCY"
  type  = "String"
  value = tostring(var.analysis_dispatch_concurrency)
}

# AI 자신의 분석 상한 (초). backend의 읽기 타임아웃보다 짧게 둔다 - 그래야 AI가 스스로 끊고
# 503을 돌려주고(BE는 일시 장애로 보고 재전송한다) 멈춘 추론이 임시파일을 붙들지 않는다.
# 반대로 두면 BE가 먼저 포기하는데 AI는 계속 추론해 GPU 슬롯과 임시파일이 그만큼 더 남는다.
resource "aws_ssm_parameter" "ai_analysis_timeout_seconds" {
  name  = "${var.ssm_prefix}/ai/ACCENTURY_AI_ANALYSIS_TIMEOUT_SECONDS"
  type  = "String"
  value = tostring(var.ai_analysis_timeout_seconds)
}

# 카카오톡 공유 웹훅의 검증 키 (KAN-164). 카카오가 웹훅마다 Authorization: KakaoAK {앱 Admin 키}로 싣고,
# backend는 그 값이 이 파라미터와 같을 때만 전송 완료로 센다. 값은 우리가 발급하는 난수가 아니라
# 카카오디벨로퍼스 콘솔([앱 설정] > [앱 키] > Admin 키)에서 읽어 오는 것이라 Terraform이 만들 수 없다.
# 그래서 자리만 만들고 값은 밖에서 넣는다 - 첫 apply 뒤에 한 번:
#   aws ssm put-parameter --overwrite --type SecureString --name /accentury/{env}/ACCENTURY_SHARE_KAKAOADMINKEY --value '<Admin 키>'
# 그 다음 backend 태스크를 새로 띄운다 (secrets는 태스크 시작 시 한 번 읽힌다, README "카카오 공유 웹훅" 절).
# 자리 표시 값으로 뜬 backend는 웹훅을 전부 401로 거부한다 - backend가 이 리터럴을 키로 인정하지 않기 때문이고
# (KakaoWebhookAuth.PLACEHOLDER, SsmEnvironmentBindingTest가 대조), 그래서 카운트가 새지도 부풀지도 않는다.
# 두 환경이 같은 카카오 앱을 쓰므로 값도 같다.
#
# value가 아니라 value_wo(write-only)다 (Codex sol 리뷰 P2). value로 두고 ignore_changes를 걸면 갱신 diff만
# 억제될 뿐 refresh가 실제 값을 읽어 state에 평문으로 남긴다 - 손으로 넣은 Admin 키가 state 읽는 쪽에
# 노출되는 자리다. write-only 인자는 provider가 read 때 value를 state에 두지 않고(has_value_wo만 남는다)
# 밖에서 바꾼 값과의 drift도 보지 않는다. 자리 표시 값을 다시 쓰게 하려면 value_wo_version을 올린다.
resource "aws_ssm_parameter" "kakao_admin_key" {
  name             = "${var.ssm_prefix}/ACCENTURY_SHARE_KAKAOADMINKEY"
  type             = "SecureString"
  value_wo         = "unset-put-parameter-after-apply"
  value_wo_version = 1
}

# 이용 후기 알림이 나가는 슬랙 채널(#feedback)의 Incoming Webhook URL (KAN-211). 개발팀이 후기를
# 읽는 곳이 이 채널 하나이고, 무료 플랜이라 수단도 Incoming Webhook 하나다. 값은 슬랙 콘솔이
# 발급하는 것이라 Terraform이 만들 수 없다 - 카카오 Admin 키와 같은 사정이라 자리만 만들고
# apply 뒤에 한 번 넣는다 (README "이용 후기 슬랙 알림" 절). write-only(value_wo)를 쓰는 이유도
# 위 kakao_admin_key 주석과 같다 - value로 두면 refresh가 손으로 넣은 값을 state에 평문으로 읽어 온다.
#
# 카카오 키와 다른 점은 하나다: 이 파라미터는 backend의 필수 설정이 아니다(DeploymentConfigGuard
# 밖이다). 자리 표시 값으로 뜬 backend는 알림만 끄고 후기는 그대로 저장하므로, apply와 이미지
# 배포의 순서 제약이 없고 값을 아직 안 넣은 채로 배포해도 아무것도 멈추지 않는다
# (FeedbackSlackNotifier). 채널이 하나라 두 환경이 같은 값을 쓰고, staging과 prod는 메시지
# 머리의 환경 라벨로 가른다.
resource "aws_ssm_parameter" "feedback_slack_webhook_url" {
  name             = "${var.ssm_prefix}/ACCENTURY_FEEDBACK_SLACKWEBHOOKURL"
  type             = "SecureString"
  value_wo         = "unset-put-parameter-after-apply"
  value_wo_version = 1
}

# ---- 앱 계정 인증 (KAN-223) ----

# Access JWT(HS256)의 서명 키. backend(AccessTokens)가 32바이트 미만을 거부하므로 64자 영숫자로 넉넉히 잡는다.
# 모든 backend 태스크가 같은 키여야 한다 - 한 태스크가 발급한 Access를 다른 태스크가 검증한다. 대칭 키라 이 값을
# 읽는 주체는 임의 사용자의 Access를 만들 수 있으므로 위 "시크릿" 절의 write-only 방식을 쓴다 (KAN-242). 회전은
# local.jwt_secret_version을 올려 apply한 뒤 backend 태스크를 새로 띄운다. 그 순간 발급된 Access(최대 30분)가
# 전부 무효가 되고, 앱은 refresh로 새로 받는다 (Refresh는 Redis라 영향이 없다).
ephemeral "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "jwt_secret" {
  name             = "${var.ssm_prefix}/ACCENTURY_AUTH_JWTSECRET"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.jwt_secret.result
  value_wo_version = local.jwt_secret_version
}

# Refresh 토큰 저장소 ElastiCache의 주소와 AUTH 토큰 (data 모듈). 이름은 Spring Boot 프로퍼티 규칙이다
# (spring.data.redis.host -> SPRING_DATA_REDIS_HOST). TLS는 application-deploy.yml이 켠다.
resource "aws_ssm_parameter" "redis_host" {
  name  = "${var.ssm_prefix}/SPRING_DATA_REDIS_HOST"
  type  = "String"
  value = var.redis_host
}

resource "aws_ssm_parameter" "redis_password" {
  name  = "${var.ssm_prefix}/SPRING_DATA_REDIS_PASSWORD"
  type  = "SecureString"
  value = var.redis_auth_token
}

# IdP 토큰이 우리 앱의 것인지 가르는 값 셋 (명세서 §3.9). 시크릿이 아니라 String이다. 값은 IdP 콘솔에서 만드는 것이라
# (KAN-224 콘솔 설정) 받기 전에는 tfvars의 기본값인 자리 표시 값이고, 그동안 그 IdP 로그인만 401이다 - 기동과 다른 IdP는
# 영향이 없다 (backend JwksIdTokens.configured). 값을 받으면 tfvars에 적고 apply한 뒤 backend 태스크를 새로 띄운다.
# 두 환경이 같은 IdP 앱을 쓰면 값도 같다.
resource "aws_ssm_parameter" "google_client_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_GOOGLECLIENTID"
  type  = "String"
  value = var.auth_google_client_id
}

resource "aws_ssm_parameter" "apple_bundle_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_APPLEBUNDLEID"
  type  = "String"
  value = var.auth_apple_bundle_id
}

resource "aws_ssm_parameter" "kakao_app_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_KAKAOAPPID"
  type  = "String"
  value = var.auth_kakao_app_id
}

# 네이버 로그인 Client ID와 Secret (KAN-243). 네이버 사용자 조회 API는 토큰의 발급 앱을 알려 주지 않아서, backend가
# SDK refresh token을 이 두 값으로 교환해 성공해야 우리 앱의 토큰으로 본다 (NaverIdpVerifier). 네이버 개발자 센터의
# 앱 설정에 있는 값이고 앱(Android, iOS)의 NAVER_CLIENT_ID, NAVER_CLIENT_SECRET과 같다.
# Client ID는 위 셋과 같이 tfvars로 넣는 String이다. Secret은 시크릿이라 tfvars에 두지 않고 카카오 Admin 키처럼
# 자리만 만든 뒤 apply 뒤에 한 번 넣는다 (README "소셜 로그인" 절):
#   aws ssm put-parameter --overwrite --type SecureString --name /accentury/{env}/ACCENTURY_AUTH_NAVERCLIENTSECRET --value '<Client Secret>'
# 그 다음 backend 태스크를 새로 띄운다. 둘 중 하나라도 자리 표시 값인 동안 네이버 로그인만 401이다.
# write-only인 이유는 kakao_admin_key 주석과 같다.
resource "aws_ssm_parameter" "naver_client_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_NAVERCLIENTID"
  type  = "String"
  value = var.auth_naver_client_id
}

resource "aws_ssm_parameter" "naver_client_secret" {
  name             = "${var.ssm_prefix}/ACCENTURY_AUTH_NAVERCLIENTSECRET"
  type             = "SecureString"
  value_wo         = "unset-put-parameter-after-apply"
  value_wo_version = 1
}

# 탈퇴한 애플 계정의 토큰 revoke (KAN-241, 애플 심사 지침 5.1.1(v)). backend가 애플 키(.p8)로 client_secret JWT를
# 서명해 앱이 보낸 authorization code를 교환하고 revoke한다 (AppleTokenRevoker). 팀 ID와 키 ID는 시크릿이 아니라
# tfvars로 넣는 String이고, 키 원문은 네이버 Secret처럼 자리만 만든 뒤 apply 뒤에 한 번 넣는다 (README "소셜 로그인" 절):
#   aws ssm put-parameter --overwrite --type SecureString --name /accentury/{env}/ACCENTURY_AUTH_APPLEPRIVATEKEY --value file://AuthKey_XXXXXXXXXX.p8
# 그 다음 backend 태스크를 새로 띄운다.
#
# 네이버 값과 다른 점은 하나다: 셋 다 backend의 필수 설정이 아니다(DeploymentConfigGuard 밖이다). 하나라도 없거나
# 자리 표시 값이면 backend가 revoke만 건너뛰고 탈퇴는 성공시키므로(feedback_slack_webhook_url과 같은 사정), apply와
# 이미지 배포의 순서 제약이 없다. write-only인 이유는 kakao_admin_key 주석과 같다.
resource "aws_ssm_parameter" "apple_team_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_APPLETEAMID"
  type  = "String"
  value = var.auth_apple_team_id
}

resource "aws_ssm_parameter" "apple_key_id" {
  name  = "${var.ssm_prefix}/ACCENTURY_AUTH_APPLEKEYID"
  type  = "String"
  value = var.auth_apple_key_id
}

resource "aws_ssm_parameter" "apple_private_key" {
  name             = "${var.ssm_prefix}/ACCENTURY_AUTH_APPLEPRIVATEKEY"
  type             = "SecureString"
  value_wo         = "unset-put-parameter-after-apply"
  value_wo_version = 1
}

# ---- 사투리 텍스트 번역 (KAN-266) ----

# Gemini API 키. Google AI Studio가 발급하는 값이라 Terraform이 만들 수 없다 - 카카오 Admin 키와 같은 사정이라 자리만
# 만들고 apply 뒤에 한 번 넣는다 (README "사투리 텍스트 번역" 절):
#   aws ssm put-parameter --overwrite --type SecureString --name /accentury/{env}/ACCENTURY_TRANSLATION_APIKEY --value '<API 키>'
# 그 다음 backend 태스크를 새로 띄운다. 환경마다 AI Studio 프로젝트와 키가 따로다 (2026-10-08 결정 - staging 실증이
# prod 무료 한도를 쓰지 않게). write-only인 이유는 kakao_admin_key 주석과 같다.
#
# 이 파라미터는 backend의 필수 설정이 아니다(DeploymentConfigGuard 밖이다). 자리 표시 값으로 뜬 backend는 번역만 503이고
# 기동과 다른 기능은 영향이 없으므로(TranslationConfig), apply와 이미지 배포의 순서 제약이 없다.
resource "aws_ssm_parameter" "translation_api_key" {
  name             = "${var.ssm_prefix}/ACCENTURY_TRANSLATION_APIKEY"
  type             = "SecureString"
  value_wo         = "unset-put-parameter-after-apply"
  value_wo_version = 1
}

# 모델 이름 - 코드 수정 없이 바꾸는 자리다 (2026-10-08 결정). 없어도 backend는 application.yml의 기본값으로 뜬다.
resource "aws_ssm_parameter" "translation_model" {
  name  = "${var.ssm_prefix}/ACCENTURY_TRANSLATION_MODEL"
  type  = "String"
  value = var.translation_model
}

# 번역 기록 버킷 스위치 - 버킷 이름을 넘긴 환경(prod)에만 생긴다. 없는 환경(staging)의 태스크에는 이 환경 변수가 아예
# 없어 backend가 S3 클라이언트도 기록 빈도 만들지 않는다 (TranslationRecordConfig의 조건이 이 프로퍼티다).
resource "aws_ssm_parameter" "translation_record_bucket" {
  count = var.translation_record_bucket_name == null ? 0 : 1

  name  = "${var.ssm_prefix}/ACCENTURY_TRANSLATION_RECORDBUCKET"
  type  = "String"
  value = var.translation_record_bucket_name
}

# ---- 음성 저장 S3 (KAN-201, KAN-269) ----

# 버킷 이름이 있는 환경에만 두 파라미터가 생긴다 - 없는 환경의 태스크 정의에는 이 환경 변수가 아예 없어 backend가
# S3 클라이언트도 저장 빈도 만들지 않는다 (TrainingConfig의 조건이 버킷 프로퍼티다). 두 환경이 같은 deploy
# 프로파일을 쓰므로 환경별 yml 없이 이 파라미터가 스위치다. 이름은 Spring 프로퍼티 규칙이다
# (accentury.training.bucket, accentury.training.key-prefix).
#
# 버킷은 두 환경이 함께 쓰는 음성 전용 버킷 하나다 (KAN-269, bootstrap/voice.tf). 환경은 키 접두사로 나뉘고, 그
# 접두사가 KEYPREFIX다 - 끝에 슬래시가 없는 환경 이름(staging, prod)이다. 태스크 역할의 PutObject가 같은 접두사
# 아래로만 열려 있어(fargate 모듈) 이 값이 환경 이름과 다르면 저장이 AccessDenied로 실패한다.
resource "aws_ssm_parameter" "training_bucket" {
  count = var.training_bucket_name == null ? 0 : 1

  name  = "${var.ssm_prefix}/ACCENTURY_TRAINING_BUCKET"
  type  = "String"
  value = var.training_bucket_name
}

resource "aws_ssm_parameter" "training_key_prefix" {
  count = var.training_bucket_name == null ? 0 : 1

  name  = "${var.ssm_prefix}/ACCENTURY_TRAINING_KEYPREFIX"
  type  = "String"
  value = var.training_key_prefix

  lifecycle {
    precondition {
      condition     = var.training_key_prefix != null
      error_message = "training_bucket_name이 있으면 training_key_prefix(환경 이름)도 있어야 합니다 (KAN-269)."
    }
  }
}
