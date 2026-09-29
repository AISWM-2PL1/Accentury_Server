# 계정 공유 리소스(호스팅 영역, 인증서)는 import하지 않고 data로 조회만 한다
# (KAN-140, 2026-08-20 확정). Terraform이 소유하지 않으므로 destroy 대상에
# 포함되지 않는다.

data "aws_route53_zone" "this" {
  name = var.hosted_zone_name
}

# 인증서 조회는 도메인 + 태그로 고정한다. domain은 주 도메인 이름만 비교하고 SAN은
# 보지 않으며(2026-08-25 실측: domain = "*.accentury.app"은 empty result), data 소스가
# SAN 목록을 내보내지도 않는다. 그래서 나중에 apex 전용 인증서가 하나 더 발급되면
# most_recent가 그것을 잡아 staging.accentury.app이 인증서 불일치로 502가 난다.
# 와일드카드 인증서 2장(us-east-1, 서울)에 accentury-role = wildcard 태그를 붙여 두고
# (KAN-119, README 사전 요건) 그 태그로만 고른다. 태그가 없으면 plan이 empty result로
# 시끄럽게 실패한다 - 조용한 502보다 낫다 (PR 리뷰 반영, 2026-08-25).
locals {
  wildcard_certificate_tags = { accentury-role = "wildcard" }
}

data "aws_acm_certificate" "cloudfront" {
  provider = aws.us_east_1

  # 발급 때 지정한 주 도메인 이름과 같아야 한다. 2026-08-24 CLI 확인 결과
  # 주 도메인은 accentury.app이고 SAN에 *.accentury.app이 있다.
  domain      = var.acm_certificate_domain
  statuses    = ["ISSUED"]
  tags        = local.wildcard_certificate_tags
  most_recent = true
}

# 같은 도메인의 서울 리전 인증서 (KAN-119의 2장째). ALB 443 리스너에 걸어 오리진
# 구간도 HTTPS로 만든다 (KAN-125). 2026-08-25 CLI 확인: accentury.app + *.accentury.app, ISSUED.
data "aws_acm_certificate" "alb" {
  domain      = var.acm_certificate_domain
  statuses    = ["ISSUED"]
  tags        = local.wildcard_certificate_tags
  most_recent = true
}

module "network" {
  source = "../../modules/network"

  env                  = var.env
  vpc_cidr             = var.vpc_cidr
  azs                  = var.azs
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}

module "data" {
  source = "../../modules/data"

  env                 = var.env
  private_subnet_ids  = module.network.private_subnet_ids
  rds_sg_id           = module.network.rds_sg_id
  redis_sg_id         = module.network.redis_sg_id
  instance_class      = var.db_instance_class
  deletion_protection = var.db_deletion_protection
  skip_final_snapshot = var.db_skip_final_snapshot
}

# ---- staging 전용 학습 데이터 S3 (KAN-201) ----

# 원본 음성은 영속 저장소에 남지 않는다는 FR-DP-01의 staging 예외다 (2026-09-08 결정). backend가 분석 종결마다
# 음성 WAV와 AI 원점수 메타 JSON을 여기 남긴다 - 모델 재학습용이다. 대상은 학습 활용에 동의한 테스터 계정의 세션뿐이고
# (SSM ACCENTURY_TRAINING_TESTERIDS), 세션 ID는 가명(HMAC)으로 바뀐다 (KAN-239, backend TrainingSpeakers).
# 보존은 동의서의 보유 기간까지다 - 수명주기 만료일이 tfvars training_retention_until이고, 없으면 plan이 실패한다.
# 두 환경의 main.tf는 같아야 하므로(KAN-140) 스위치는 tfvars의
# training_bucket_enabled이고, prod는 false라 버킷도 정책도 파라미터도 생기지 않아 prod plan에 이 이름이 없다.
# 이름은 web 버킷 규약(edge 모듈)대로 계정 ID를 붙인다. force_destroy는 web 버킷과 같이 teardown을 위해서다 -
# staging을 부수고 다시 지으면 모인 샘플도 함께 사라진다 (README).
data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "training" {
  count = var.training_bucket_enabled ? 1 : 0

  bucket        = "accentury-${var.env}-training-${data.aws_caller_identity.current.account_id}"
  force_destroy = true

  lifecycle {
    # 기한 없는 보존을 막는다 (KAN-239) - 켜는 순간 동의서의 보유 기간이 함께 정해져 있어야 한다.
    precondition {
      condition     = var.training_retention_until != null
      error_message = "training_bucket_enabled = true이면 training_retention_until(동의서의 보유 기간 만료일)이 있어야 한다 (KAN-239)."
    }
  }
}

resource "aws_s3_bucket_public_access_block" "training" {
  count = var.training_bucket_enabled ? 1 : 0

  bucket = aws_s3_bucket.training[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# SSE-S3 기본 암호화. 버전 관리는 켜지 않는다 - 키에 분석 작업 ID가 들어 덮어쓰기가 없다 (객체 규약).
resource "aws_s3_bucket_server_side_encryption_configuration" "training" {
  count = var.training_bucket_enabled ? 1 : 0

  bucket = aws_s3_bucket.training[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 보유 기간 만료 (KAN-239). 동의서의 보유 기간 끝날과 같은 날이다. S3의 만료는 비동기라 그날 즉시 지워진다는 보장이
# 없다 - 그날이 오면 README "환경 teardown 파기 런북" 절대로 버킷을 비우고 기록을 남긴다. 이 규칙은 그 뒤의 안전망이다.
resource "aws_s3_bucket_lifecycle_configuration" "training" {
  count = var.training_bucket_enabled ? 1 : 0

  bucket = aws_s3_bucket.training[0].id

  rule {
    id     = "consent-retention"
    status = "Enabled"

    filter {}

    expiration {
      date = var.training_retention_until
    }
  }
}

# 학습 데이터를 읽는 유일한 역할 (KAN-239). 학습 담당이 이 역할을 맡아(AssumeRole) 읽는다 - 계정 안의 다른 주체는
# s3:GetObject 권한이 있어도 아래 버킷 정책이 거부한다. 신뢰 대상은 tfvars training_reader_principals이고,
# 비우면 이 계정 루트다(= 계정 안에서 sts:AssumeRole을 허용받은 IAM 주체).
resource "aws_iam_role" "training_reader" {
  count = var.training_bucket_enabled ? 1 : 0

  name = "accentury-${var.env}-training-reader"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "sts:AssumeRole"
      Principal = {
        AWS = coalescelist(var.training_reader_principals,
        ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"])
      }
    }]
  })
}

resource "aws_iam_role_policy" "training_reader" {
  count = var.training_bucket_enabled ? 1 : 0

  name = "read-training-bucket"
  role = aws_iam_role.training_reader[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.training[0].arn
      },
      {
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.training[0].arn}/*"
      },
    ]
  })
}

# 버킷 정책 (KAN-239). 두 문장 다 Deny라 IAM 쪽 허용이 넓어도 이긴다.
# 1) TLS가 아닌 요청은 전부 거부한다.
# 2) 객체 본문(GetObject)은 학습 읽기 역할만 - 관리자 자격 증명도 거부된다. ListBucket은 거부하지 않는다: HeadBucket이
#    s3:ListBucket 권한으로 판정되므로 거부하면 Terraform의 버킷 refresh가 막혀 이후 plan, apply와 파기 apply까지 잠긴다
#    (Codex astra 리뷰 P1). 키에는 가명(speaker, sampleId)과 지역, 문항 ID만 있어 목록이 보여도 음성과 점수는 못 읽는다.
#    버킷을 비울 때는 training_bucket_enabled = false apply다 (force_destroy, README "환경 teardown 파기 런북" 절).
resource "aws_s3_bucket_policy" "training" {
  count = var.training_bucket_enabled ? 1 : 0

  bucket = aws_s3_bucket.training[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [aws_s3_bucket.training[0].arn, "${aws_s3_bucket.training[0].arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      },
      {
        Sid       = "ObjectReadOnlyByTrainingReader"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.training[0].arn}/*"
        Condition = { ArnNotEquals = { "aws:PrincipalArn" = aws_iam_role.training_reader[0].arn } }
      },
    ]
  })

  # 퍼블릭 차단이 먼저 서야 한다 - 정책을 먼저 붙이면 차단 설정 전 잠깐이라도 정책이 평가된다(여기서는 Deny뿐이라
  # 무해하지만, 이후 문장이 늘 때를 위해 순서를 고정한다).
  depends_on = [aws_s3_bucket_public_access_block.training]
}

# backend, ai 컨테이너 환경 변수 (KAN-129, KAN-36). 값이 network, data 모듈 출력이라 여기서 조립한다.
module "config" {
  source = "../../modules/config"

  ssm_prefix                 = var.ssm_prefix
  domain                     = var.domain
  vpc_cidr                   = var.vpc_cidr
  rds_endpoint               = module.data.endpoint
  rds_master_user_secret_arn = module.data.master_user_secret_arn
  ai_dns_name                = module.network.ai_dns_name
  # 앱 계정 인증 (KAN-223) - Refresh 저장소 접속과 IdP 값. IdP 값은 콘솔에서 받기 전까지 tfvars의 자리 표시 값이다.
  redis_host            = module.data.redis_host
  redis_auth_token      = module.data.redis_auth_token
  auth_google_client_id = var.auth_google_client_id
  auth_apple_bundle_id  = var.auth_apple_bundle_id
  auth_kakao_app_id     = var.auth_kakao_app_id
  auth_naver_client_id  = var.auth_naver_client_id
  # 학습 데이터 버킷이 있는 환경(staging)에만 ACCENTURY_TRAINING_CONSENTEDBUCKET 파라미터가 생긴다 (KAN-201).
  training_bucket_name = one(aws_s3_bucket.training[*].bucket)
}

# 커스텀 지표 네임스페이스 (KAN-36). 지표를 올리는 역할의 PutMetricData 조건과 경보가 같은 이름을 봐야 하므로
# 한 곳에서 정한다. backend 것은 application-deploy.yml의 management.cloudwatch.metrics.export.namespace와도 같다.
locals {
  backend_metric_namespace = "accentury/backend"
  ai_metric_namespace      = "accentury/ai"
}

# CloudFront 앞단 WAF (KAN-149). CLOUDFRONT 스코프 웹 ACL과 그 로그 그룹은 us-east-1에만
# 만들 수 있어 프로바이더 별칭을 넘긴다. Count/Block 전환은 tfvars의 waf_enforce로 한다.
module "waf" {
  source = "../../modules/waf"

  providers = {
    aws = aws.us_east_1
  }

  env        = var.env
  enforce    = var.waf_enforce
  rate_limit = var.waf_rate_limit
}

# internal ALB(대상 그룹 ip), VPC 오리진, CloudFront, S3. 대상 등록은 ECS 서비스가 한다 (KAN-165).
module "edge" {
  source = "../../modules/edge"

  env                 = var.env
  domain              = var.domain
  vpc_id              = module.network.vpc_id
  private_subnet_ids  = module.network.private_subnet_ids
  alb_sg_id           = module.network.alb_sg_id
  acm_certificate_arn = data.aws_acm_certificate.cloudfront.arn
  alb_certificate_arn = data.aws_acm_certificate.alb.arn
  zone_id             = data.aws_route53_zone.this.zone_id
  web_acl_arn         = module.waf.web_acl_arn
}

# backend - ECS Fargate 서비스 (KAN-165). 0.5 vCPU / 2 GB 온디맨드, 목표 추적 오토스케일링 min 1 max 3 (KAN-168).
# min/max와 목표값은 두 환경이 같아야 하므로 tfvars가 아니라 모듈 기본값이다.
# 이미지 태그는 SSM IMAGE_TAG(파이프라인 소유)를 읽으므로 첫 apply 전에 그 파라미터가 있어야 한다 (README).
module "fargate" {
  source = "../../modules/fargate"

  env                        = var.env
  subnet_ids                 = module.network.public_subnet_ids
  security_group_id          = module.network.backend_sg_id
  ssm_prefix                 = var.ssm_prefix
  rds_master_user_secret_arn = module.data.master_user_secret_arn
  metric_namespace           = local.backend_metric_namespace
  target_group_arn           = module.edge.target_group_arn
  alb_listener_arn           = module.edge.https_listener_arn
  # 목표 추적 지표(ALBRequestCountPerTarget)의 resource_label 재료 (KAN-168).
  alb_arn_suffix          = module.edge.alb_arn_suffix
  target_group_arn_suffix = module.edge.target_group_arn_suffix
  # 태스크 정의 secrets로 전부 주입한다 - 실행 역할도 이 목록만 읽는다.
  config_parameter_names = module.config.parameter_names
  # 학습 데이터 버킷이 있는 환경(staging)에만 태스크 역할에 PutObject 문장이 생긴다 (KAN-201).
  training_bucket_arn = one(aws_s3_bucket.training[*].arn)
}

# ai 호스트 - 내부 ALB 뒤 ASG(min 1, max = tfvars ai_max_size)의 전용 추론 EC2 (KAN-36, ALB와 오토스케일링은
# KAN-201). 인스턴스 유형은 2026-09-01 결정으로 처음부터 c7i.xlarge이고, 루트 볼륨만 실모델 전환(B단계)에서
# tfvars로 40GB가 된다. 스케일링 기준은 backend 지표(accentury.analysis.processing)라 그 네임스페이스를 넘긴다.
module "ai_host" {
  source = "../../modules/ai-host"

  env                   = var.env
  vpc_id                = module.network.vpc_id
  subnet_ids            = module.network.public_subnet_ids
  security_group_id     = module.network.ai_sg_id
  alb_security_group_id = module.network.ai_alb_sg_id
  instance_type         = var.ai_instance_type
  root_volume_size      = var.ai_root_volume_size
  max_size              = var.ai_max_size
  ssm_prefix            = var.ssm_prefix
  metric_namespace      = local.ai_metric_namespace
  # ai 호스트 역할은 자기 하위 경로(/ai)와 IMAGE_TAG만 읽는다 - 내부 호출 토큰이 먼저 있어야 한다.
  config_parameter_names   = module.config.ai_parameter_names
  private_zone_id          = module.network.private_zone_id
  dns_name                 = module.network.ai_dns_name
  vpc_cidr                 = var.vpc_cidr
  scaling_metric_namespace = local.backend_metric_namespace
}

# KAN-36에서는 compute 모듈을 role = "ai"로 부른 module.ai_compute였다 (backend 역할 호출 module.compute는
# KAN-165에서 Fargate 서비스로 대체돼 사라진다). 살아 있는 state에서 주소만 옮긴다.
moved {
  from = module.ai_compute
  to   = module.ai_host
}

# 배포 파이프라인 역할 (KAN-127). GitHub Actions가 OIDC로 맡는다. 공급자는 bootstrap 소유.
module "deploy" {
  source = "../../modules/deploy"

  env                         = var.env
  github_owner                = var.github_owner
  github_owner_id             = var.github_owner_id
  github_repositories         = var.github_repositories
  ssm_prefix                  = var.ssm_prefix
  ci_image_push               = var.ci_image_push
  web_bucket_arn              = module.edge.web_bucket_arn
  cloudfront_distribution_arn = module.edge.distribution_arn

  ecs = {
    cluster_arn                = module.fargate.cluster_arn
    service_arn                = module.fargate.service_arn
    task_definition_family_arn = module.fargate.task_definition_family_arn
    task_role_arn              = module.fargate.task_role_arn
    execution_role_arn         = module.fargate.execution_role_arn
  }
}

# 최소 알림 (KAN-134) + backend 서비스 경보 2종 (KAN-165) + AI 호스트 경보 2종 (KAN-36). ALB, RDS, ECS 표준
# 지표에 backend의 회로 상태 게이지와 ai 호스트의 health 커스텀 지표를 더한다. 전체 관측성은 KAN-38.
module "monitoring" {
  source = "../../modules/monitoring"

  env                      = var.env
  alert_email              = var.alert_email
  alb_arn_suffix           = module.edge.alb_arn_suffix
  target_group_arn_suffix  = module.edge.target_group_arn_suffix
  db_instance_identifier   = module.data.instance_identifier
  ecs_cluster_name         = module.fargate.cluster_name
  ecs_service_name         = module.fargate.service_name
  ai_metric_namespace      = local.ai_metric_namespace
  backend_metric_namespace = local.backend_metric_namespace
  # AI 대상 그룹의 healthy 대상 경보 (KAN-201).
  ai_alb_arn_suffix          = module.ai_host.alb_arn_suffix
  ai_target_group_arn_suffix = module.ai_host.target_group_arn_suffix
}
