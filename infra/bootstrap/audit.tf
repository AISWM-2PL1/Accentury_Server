# 계정 감사와 위협 탐지 (KAN-245, 보안 검토 #15). 계정에 하나씩만 두는 리소스라 bootstrap에 둔다.
#
# 이 전에는 state 읽기, 학습 음성 다운로드, 시크릿 조회를 누가 했는지 남지 않았다. CloudTrail 이벤트 기록은
# 90일만 콘솔에서 보이고 데이터 이벤트(S3 객체 읽기)는 아예 없다.
#   - 관리 이벤트 trail 1개: 읽기와 쓰기 모두, 전 리전, 로그 파일 검증. 계정의 첫 관리 이벤트 trail은 무료다.
#   - S3 데이터 이벤트: 학습 버킷(음성)과 tfstate 버킷(state)만. 객체 이벤트 10만 건당 0.10달러라 버킷을 좁힌다.
#   - GuardDuty: 서울 리전 detector + S3 데이터 이벤트 보호. 첫 30일 무료, 이후 이 규모에서 월 몇 달러로 추정한다.

locals {
  audit_trail_name = "accentury-account-audit"

  # 학습 버킷은 지금 staging에만 있다 (KAN-239). prod 이름도 미리 넣어 두면 그 버킷이 생기는 날 바로 기록된다 -
  # 없는 버킷의 ARN은 selector에서 아무 것도 고르지 않을 뿐 오류가 아니다.
  audit_data_event_bucket_arns = [
    "arn:aws:s3:::accentury-staging-training-${data.aws_caller_identity.current.account_id}",
    "arn:aws:s3:::accentury-prod-training-${data.aws_caller_identity.current.account_id}",
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
    name = "S3 object events on training and tfstate buckets"

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

output "audit_bucket" {
  value       = aws_s3_bucket.audit.bucket
  description = "CloudTrail 로그 버킷 (1년 보관)"
}
