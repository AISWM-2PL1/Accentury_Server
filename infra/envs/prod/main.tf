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

# ---- 음성 저장 S3 (KAN-201, KAN-269) ----

# 음성 저장(선택 동의)에 동의한 세션의 음성 WAV와 AI 원점수 메타 JSON은 두 환경이 함께 쓰는 음성 전용 버킷에
# 남는다 (KAN-269). 버킷은 bootstrap 스택이 만들고(bootstrap/voice.tf) 이 스택은 이름만 같은 규칙으로 조립한다 -
# 버킷이 환경 스택 밖에 있어 환경을 부수거나 스위치를 꺼도 음성은 그대로다. 환경은 키 접두사(환경 이름)로 나뉘고,
# 태스크 역할의 PutObject가 자기 접두사 아래로만 열린다 (fargate 모듈). 두 환경의 main.tf는 같아야 하므로(KAN-140)
# 스위치는 tfvars의 training_bucket_enabled이고, 끄면 파라미터도 권한도 생기지 않는다.
# 적용 순서: bootstrap이 먼저다 - 버킷이 없는 채로 이 스택만 적용하면 backend 저장이 NoSuchBucket으로 실패한다.
data "aws_caller_identity" "current" {}

locals {
  voice_bucket_name = "accentury-voice-${data.aws_caller_identity.current.account_id}"
}

# 옛 환경별 학습 버킷(accentury-<env>-training-<계정 ID>, KAN-201)을 지우지 않고 Terraform 관리에서만 뺀다.
# 음성은 다시 만들 수 없는 데이터라 Terraform이 지워서는 안 된다. 이 버킷은 force_destroy = true로 만들어져 있어서
# resource 블록을 그냥 지우면 다음 apply가 모인 음성째 버킷을 지운다. removed 블록의 destroy = false는 state에서만
# 빼고 실제 버킷과 객체는 그대로 둔다 - plan에 "will no longer be managed"로 보여야 하고, "will be destroyed"로
# 보이면 apply하지 않는다. 남은 옛 버킷을 옮길지 비울지는 사람이 정하고 사람이 한다.
# count가 붙어 있던 리소스라 from에는 인덱스 없이 주소만 적는다 (인스턴스 전부가 대상). state에 인스턴스가 없는
# 환경(prod)에서는 아무 일도 하지 않는다. 두 환경에 한 번씩 적용된 뒤에는 이 블록들을 지워도 된다.
removed {
  from = aws_s3_bucket.training

  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_public_access_block.training

  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_server_side_encryption_configuration.training

  lifecycle {
    destroy = false
  }
}

# 옛 버킷의 읽기 제한(KAN-239의 버킷 정책과 학습 읽기 역할)도 지우지 않고 관리에서만 뺀다. 이 둘은 PR #21에서
# 코드가 먼저 지워졌는데 staging에는 실물이 남아 있어, 그대로 apply하면 삭제되고 옛 버킷의 음성이 S3 읽기 권한을
# 가진 모든 주체에게 열린다 (KAN-269 리뷰 P2). 버킷을 사람이 정리할 때까지 정책과 역할을 그대로 둔다.
# 수명주기 만료 규칙(2026-12-31 자동 삭제)은 여기 없다 - 그 규칙은 지워져야 음성이 삭제되지 않으므로 apply가
# 삭제하도록 둔다.
removed {
  from = aws_s3_bucket_policy.training

  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_iam_role.training_reader

  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_iam_role_policy.training_reader

  lifecycle {
    destroy = false
  }
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
  auth_apple_team_id    = var.auth_apple_team_id
  auth_apple_key_id     = var.auth_apple_key_id
  # 음성 저장을 켠 환경에만 ACCENTURY_TRAINING_BUCKET, ACCENTURY_TRAINING_KEYPREFIX 파라미터가 생긴다 (KAN-269).
  training_bucket_name = var.training_bucket_enabled ? local.voice_bucket_name : null
  training_key_prefix  = var.training_bucket_enabled ? var.env : null
  # 사투리 텍스트 번역 (KAN-266) - 모델 이름은 모든 환경에, 기록 버킷 스위치는 기록을 켠 환경(prod)에만 생긴다.
  translation_model              = var.translation_model
  translation_record_bucket_name = var.translation_records_enabled ? aws_s3_bucket.translation_records[0].bucket : null
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

  env                   = var.env
  enforce               = var.waf_enforce
  rate_limit            = var.waf_rate_limit
  auth_rate_limit       = var.waf_auth_rate_limit
  admin_rate_limit      = var.waf_admin_rate_limit
  ip_reputation_enforce = var.waf_ip_reputation_enforce
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
  # 음성 저장을 켠 환경에만 태스크 역할에 PutObject 문장이 생긴다 - 음성 버킷의 자기 환경 접두사 아래로만이다 (KAN-269).
  training_bucket_arn = var.training_bucket_enabled ? "arn:aws:s3:::${local.voice_bucket_name}" : null
  training_key_prefix = var.training_bucket_enabled ? var.env : null
  # 번역 기록을 켠 환경(prod)에만 태스크 역할에 translations/ PutObject 문장이 생긴다 (KAN-266).
  translation_record_bucket_arn = var.translation_records_enabled ? aws_s3_bucket.translation_records[0].arn : null
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
  ami_id                = var.ai_ami_id
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

# ---- 사투리 텍스트 번역 기록 S3 (KAN-266) ----

# 사람들이 어떤 문장을 번역해 달라고 했는지 요청마다 JSON 객체 하나로 남긴다 (2026-10-08 결정). prod만이다 - staging은
# 비용 때문에 버킷을 만들지 않는다. 두 환경의 main.tf는 같아야 하므로(KAN-140) 스위치는 tfvars의
# translation_records_enabled다 (음성 버킷의 training_bucket_enabled와 같은 방식).
#   - 객체 키는 translations/yyyy/MM/dd/<요청 ID>.json(KST)이고 본문에는 계정 ID 대신 대체 ID만 있다 (명세서 §3.18).
#   - 버전 관리를 켜고 수명주기(만료) 규칙은 두지 않는다 (2026-10-08 결정 - 만료 없음). force_destroy도 없다.
#   - 쓰기는 backend 태스크 역할의 translations/ PutObject뿐이다 (fargate 모듈). 읽기는 팀 전원이다 - 아래 정책이
#     목록의 주체에게 읽기를 허용하고 그 밖의 주체에게는 거부한다.
locals {
  translation_record_bucket_name = "accentury-translator-prompt-${data.aws_caller_identity.current.account_id}"
  translation_record_reader_principal_arns = coalesce(var.translation_record_reader_principal_arns, [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/accentury-cli",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/jaeyoung",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/seongju",
  ])
}

resource "aws_s3_bucket" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  bucket = local.translation_record_bucket_name

  # 번역 기록은 다시 만들 수 없다. 이 블록을 지우거나 스위치를 끄거나 환경을 destroy하려 하면 plan이 실패한다.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  bucket = aws_s3_bucket.translation_records[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  bucket = aws_s3_bucket.translation_records[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  bucket = aws_s3_bucket.translation_records[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 버킷 정책. 음성 버킷(bootstrap/voice.tf)과 같은 두 Deny에 팀 읽기 Allow를 더한다.
# 1) TLS가 아닌 요청은 전부 거부한다.
# 2) 객체 읽기(GetObject, GetObjectVersion)는 목록의 주체만 한다 - 목록 밖이면 관리자 자격 증명도 거부된다.
# 3) 목록의 주체에게 객체 읽기와 나열을 허용한다. 같은 계정 주체라 버킷 정책의 Allow만으로 읽힌다 - 팀원의 IAM
#    정책에 S3 읽기가 따로 없어도 된다 (2026-10-08 결정 - 팀 전원).
# List, Put, Delete는 거부하지 않는다 - HeadBucket이 ListBucket 권한으로 판정되어 List를 거부하면 Terraform refresh가
# 막힌다 (KAN-239 리뷰). 쓰기는 backend 태스크 역할이 translations/ 아래로 좁힌다.
data "aws_iam_policy_document" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.translation_records[0].arn,
      "${aws_s3_bucket.translation_records[0].arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid       = "ObjectReadOnlyByTeam"
    effect    = "Deny"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${aws_s3_bucket.translation_records[0].arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    # 값이 여러 개면 어느 것과도 같지 않을 때만 거부된다 (부정 연산자의 다중 값은 NOR).
    condition {
      test     = "ArnNotEquals"
      variable = "aws:PrincipalArn"
      values   = local.translation_record_reader_principal_arns
    }
  }

  statement {
    sid       = "TeamReadObjects"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${aws_s3_bucket.translation_records[0].arn}/*"]

    principals {
      type        = "AWS"
      identifiers = local.translation_record_reader_principal_arns
    }
  }

  statement {
    sid       = "TeamListBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket", "s3:ListBucketVersions"]
    resources = [aws_s3_bucket.translation_records[0].arn]

    principals {
      type        = "AWS"
      identifiers = local.translation_record_reader_principal_arns
    }
  }
}

resource "aws_s3_bucket_policy" "translation_records" {
  count = var.translation_records_enabled ? 1 : 0

  bucket = aws_s3_bucket.translation_records[0].id
  policy = data.aws_iam_policy_document.translation_records[0].json

  # 퍼블릭 액세스 차단과 정책을 동시에 바꾸면 S3가 OperationAborted로 거절할 수 있다 (음성 버킷과 같은 이유).
  depends_on = [aws_s3_bucket_public_access_block.translation_records]
}
