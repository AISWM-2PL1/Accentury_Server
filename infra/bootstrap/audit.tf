# 계정 감사와 위협 탐지 (KAN-245, 보안 검토 #15). 계정에 하나씩만 두는 리소스라 bootstrap에 둔다.
#
# 이 전에는 state 읽기, 학습 음성 다운로드, 시크릿 조회를 누가 했는지 남지 않았다. CloudTrail 이벤트 기록은
# 90일만 콘솔에서 보이고 데이터 이벤트(S3 객체 읽기)는 아예 없다.
#   - 관리 이벤트 trail 1개: 읽기와 쓰기 모두, 전 리전, 로그 파일 검증. 계정의 첫 관리 이벤트 trail은 무료다.
#   - S3 데이터 이벤트: 음성 버킷(voice.tf), 옛 staging 학습 버킷, tfstate 버킷(state)만. 객체 이벤트 10만 건당 0.10달러라 버킷을 좁힌다.
#   - GuardDuty: 서울 리전 detector + S3 데이터 이벤트 보호. 첫 30일 무료, 이후 이 규모에서 월 몇 달러로 추정한다.

locals {
  audit_trail_name = "accentury-account-audit"

  # 음성은 두 환경이 함께 쓰는 음성 전용 버킷 하나에 모인다 (KAN-269, voice.tf). 옛 staging 학습 버킷
  # (accentury-staging-training)은 Terraform 관리에서 빠졌지만 음성이 남아 있어, 사람이 그 버킷을 정리할 때까지
  # 객체 읽기를 계속 기록한다 (KAN-269 리뷰 P2). 정리된 뒤에 이 줄을 지운다. prod에는 옛 버킷이 없었다.
  audit_data_event_bucket_arns = [
    aws_s3_bucket.voice.arn,
    "arn:aws:s3:::accentury-staging-training-${data.aws_caller_identity.current.account_id}",
    aws_s3_bucket.tfstate.arn,
  ]

  audit_trail_arn = "arn:aws:cloudtrail:ap-northeast-2:${data.aws_caller_identity.current.account_id}:trail/${local.audit_trail_name}"
}

# ---- 로그 버킷 ----

resource "aws_s3_bucket" "audit" {
  bucket = "accentury-cloudtrail-${data.aws_caller_identity.current.account_id}"

  # 감사 기록은 사고 조사의 근거라 실수로 지워지면 안 된다 (tfstate 버킷과 같은 이유).
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "audit" {
  bucket = aws_s3_bucket.audit.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit" {
  bucket = aws_s3_bucket.audit.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 1년 보관 뒤 만료 (티켓 요구사항). 그보다 오래된 기록이 필요한 규제 요건은 없다.
resource "aws_s3_bucket_lifecycle_configuration" "audit" {
  bucket = aws_s3_bucket.audit.id

  rule {
    id     = "expire-after-one-year"
    status = "Enabled"

    filter {}

    expiration {
      days = 365
    }
  }
}

# CloudTrail 쓰기 허용(이 trail만, aws:SourceArn) + TLS가 아닌 요청 거부. 문장 모양은 provider 문서의 예와 같다.
data "aws_iam_policy_document" "audit" {
  statement {
    sid       = "AWSCloudTrailAclCheck"
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.audit.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.audit_trail_arn]
    }
  }

  statement {
    sid       = "AWSCloudTrailWrite"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.audit.arn}/AWSLogs/${data.aws_caller_identity.current.account_id}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.audit_trail_arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.audit.arn,
      "${aws_s3_bucket.audit.arn}/*",
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
}

resource "aws_s3_bucket_policy" "audit" {
  bucket = aws_s3_bucket.audit.id
  policy = data.aws_iam_policy_document.audit.json

  # 퍼블릭 액세스 차단과 정책을 동시에 바꾸면 S3가 OperationAborted로 거절할 수 있다 (tfstate 버킷과 같은 이유).
  depends_on = [aws_s3_bucket_public_access_block.audit]
}

# ---- trail ----

resource "aws_cloudtrail" "audit" {
  name                          = local.audit_trail_name
  s3_bucket_name                = aws_s3_bucket.audit.id
  is_multi_region_trail         = true
  include_global_service_events = true
  enable_log_file_validation    = true

  # 고급 선택기를 쓰면 관리 이벤트도 선택기로 적어야 기록된다. SSM GetParameter, Secrets Manager GetSecretValue
  # 같은 읽기도 관리 이벤트다.
  advanced_event_selector {
    name = "management events, read and write"

    field_selector {
      field  = "eventCategory"
      equals = ["Management"]
    }
  }

  advanced_event_selector {
    name = "S3 object events on voice and tfstate buckets"

    field_selector {
      field  = "eventCategory"
      equals = ["Data"]
    }

    field_selector {
      field  = "resources.type"
      equals = ["AWS::S3::Object"]
    }

    field_selector {
      field = "resources.ARN"
      # 끝의 슬래시는 의도다 - 이름이 같은 접두로 시작하는 다른 버킷을 고르지 않는다 (provider 문서).
      starts_with = [for arn in local.audit_data_event_bucket_arns : "${arn}/"]
    }
  }

  depends_on = [aws_s3_bucket_policy.audit]
}

# ---- GuardDuty ----

# GuardDuty는 리전 단위다. 리소스가 전부 서울에 있어 서울 detector 하나로 둔다.
resource "aws_guardduty_detector" "this" {
  enable = true
}

resource "aws_guardduty_detector_feature" "s3_data_events" {
  detector_id = aws_guardduty_detector.this.id
  name        = "S3_DATA_EVENTS"
  status      = "ENABLED"
}

# detector를 만들면 AWS가 몇 가지 보호 기능을 기본으로 켠다 (2026-10-03 staging 적용 뒤 확인). 쓰는 것만 남기고
# 상태를 코드로 고정한다 (사용자 결정 2026-10-03):
#   - RDS_LOGIN_EVENTS: 켬. RDS 로그인 이상(무차별 대입, 낯선 출처)을 본다 - 우리 DB에 실제로 쓸모가 있다.
#   - EKS_AUDIT_LOGS, LAMBDA_NETWORK_LOGS: 끔. EKS와 Lambda를 쓰지 않는다.
#   - EBS_MALWARE_PROTECTION: 끔. 발견 사항이 생길 때마다 EBS 스캔 요금이 붙는데, AI 호스트는 교체형이라 의심되면
#     스캔보다 교체가 싸다.
#   - RDS_LOGIN_EVENTS 밑의 RDS_DATA_RISK(RDS 데이터 활동 감시): 꺼진 채로 둔다. AWS가 하위 설정으로 꺼 둔 채 붙여
#     두었는데, provider(6.61.0)가 additional_configuration 이름으로 RDS_DATA_RISK를 받지 않아 코드로 고정할 수 없다.
#     코드에 없으면 plan이 매번 지우려 하므로 additional_configuration을 무시한다 (2026-11-12 KAN-310 apply 뒤 확인).
#     그래서 콘솔에서 이것을 켜도 plan에 나오지 않는다. 켜고 끄는 것은 콘솔에서 한다.
# 주의: 이 리소스를 지워도 기능은 꺼지지 않고 state에서만 빠진다 (provider 문서). 끄려면 DISABLED로 apply한다.
resource "aws_guardduty_detector_feature" "managed" {
  for_each = {
    RDS_LOGIN_EVENTS       = "ENABLED"
    EKS_AUDIT_LOGS         = "DISABLED"
    LAMBDA_NETWORK_LOGS    = "DISABLED"
    EBS_MALWARE_PROTECTION = "DISABLED"
  }

  detector_id = aws_guardduty_detector.this.id
  name        = each.key
  status      = each.value

  lifecycle {
    ignore_changes = [additional_configuration]
  }
}

# ---- GuardDuty 결과 -> 슬랙 #infra-alerts (KAN-310) ----

# 결과가 콘솔에만 쌓이면 사고가 나도 사람이 모른다. 심각도 4 이상(Medium, High, Critical)을 EventBridge로 prod의
# #infra-alerts 토픽에 넣는다. Low(1~3.9)는 잡음이 많아 뺀다. 심각도 구간은 Low 1~3.9, Medium 4~6.9, High 7~8.9,
# Critical 9~10이다 (GuardDuty 문서 "Severity levels of GuardDuty findings").
#
# 대상은 prod 토픽 하나다. GuardDuty가 계정 단위라 환경마다 보내면 같은 결과가 두 번 오고, 채널 설정도 prod가 갖는다
# (KAN-273). bootstrap은 환경 state를 읽지 않으므로 토픽 ARN을 이름 규칙으로 조립한다. 토픽 정책의 허용(이 규칙의 ARN
# 조건)은 prod의 slack-alerts 모듈이 갖는다(slack_guardduty_findings_enabled). 그래서 처음 적용은 prod env가 먼저다 -
# 이쪽이 먼저면 그사이 결과는 publish가 거부되어 규칙의 FailedInvocations 지표에만 남는다. 규칙 이름을 바꾸면 그 모듈의
# local.guardduty_findings_rule_arn도 같이 바꾼다.
#
# 입력 변환 없이 원본 이벤트를 그대로 보낸다. Chatbot이 GuardDuty 이벤트를 자체 형식으로 그린다 (Chatbot 관리자 안내서
# "Using Amazon Q Developer in chat applications with other AWS services").
#
# 이미 있는 결과가 다시 생기면 GuardDuty가 6시간마다 묶어서 다시 보낸다(detector 기본 SIX_HOURS). 같은 결과가 6시간
# 간격으로 다시 오면 아직 이어지고 있다는 뜻이다.
data "aws_region" "current" {}

locals {
  guardduty_findings_rule_name = "accentury-guardduty-findings"
  guardduty_findings_topic_arn = "arn:aws:sns:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:accentury-prod-slack-infra-alerts"
}

resource "aws_cloudwatch_event_rule" "guardduty_findings" {
  name        = local.guardduty_findings_rule_name
  description = "GuardDuty findings with severity >= 4 -> prod #infra-alerts (KAN-310)"

  event_pattern = jsonencode({
    source      = ["aws.guardduty"]
    detail-type = ["GuardDuty Finding"]
    detail = {
      severity = [{ numeric = [">=", 4] }]
    }
  })

  tags = { Name = local.guardduty_findings_rule_name }
}

resource "aws_cloudwatch_event_target" "guardduty_findings" {
  rule      = aws_cloudwatch_event_rule.guardduty_findings.name
  target_id = "slack-infra-alerts"
  arn       = local.guardduty_findings_topic_arn
}

output "audit_bucket" {
  value       = aws_s3_bucket.audit.bucket
  description = "CloudTrail 로그 버킷 (1년 보관)"
}
