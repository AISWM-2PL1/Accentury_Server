# Terraform state 백엔드(S3 버킷) 생성 전용 스택 (KAN-140).
#
# 닭과 달걀 문제 때문에 처음에는 로컬 state로 만들었다 - state를 담을 버킷을
# Terraform으로 만들려면 그 시점에는 아직 원격 백엔드가 없다. 버킷이 생긴 뒤
# 2026-10-03(KAN-245)에 state를 그 버킷의 bootstrap/terraform.tfstate로 옮겼다 (backend.tf).
# 버킷부터 다시 만들어야 하면 backend.tf의 안내를 따르고, 리소스가 이미 있으면 import로 복구한다:
#   terraform import aws_s3_bucket.tfstate accentury-tfstate-<account_id>

terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      project    = "accentury"
      managed-by = "terraform"
      # 두 환경이 공유하는 계정 단위 리소스라 env를 특정할 수 없다 (KAN-35 비용 태그).
      env = "shared"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "tfstate" {
  bucket = "accentury-tfstate-${data.aws_caller_identity.current.account_id}"

  # state 파일에는 리소스 식별자와 일부 민감값이 들어간다. 실수로 destroy에
  # 쓸려 나가면 두 환경의 state가 통째로 사라지므로 코드 수준에서 막는다.
  lifecycle {
    prevent_destroy = true
  }
}

# state 파일 히스토리 보존 - 잘못된 apply 뒤 이전 state로 복구하는 유일한 수단이다.
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 옛 state 버전의 보존 기한 (KAN-242). state에는 값이 남는 시크릿이 있었고(지금도 Redis AUTH 토큰이 남는다,
# modules/data 주석) 버전 관리가 그 옛 값을 무기한 붙들고 있었다. 회전으로 값을 바꿔도 이전 버전 객체에는 옛 값이
# 그대로라, 기한을 두어 회전 전 값이 언젠가는 사라지게 한다. 30일은 "잘못된 apply 뒤 이전 state로 복구"(위 버전
# 관리 주석)에 충분한 창이다 - 문제를 30일 넘게 모르고 지나가는 경우는 복구보다 재import가 낫다.
#
# 삭제 표시 정리는 네이티브 잠금 파일(use_lockfile, .tflock) 때문이다. 잠금은 apply마다 객체를 만들고 지우므로
# 버전 관리 버킷에는 삭제 표시가 쌓인다. 옛 버전이 만료로 사라진 뒤 홀로 남은 삭제 표시만 지운다.
resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    id     = "expire-noncurrent-state"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    expiration {
      expired_object_delete_marker = true
    }
  }

  # 버전 관리가 켜진 뒤에 붙어야 noncurrent 규칙이 의미가 있다 (provider 문서 권고).
  depends_on = [aws_s3_bucket_versioning.tfstate]
}

# TLS가 아닌 요청 거부 (KAN-242). 읽기 주체를 Terraform 운영자 역할로 좁히는 문장은 넣지 않는다 - 역할 목록이
# 틀리면 state에 아무도 못 들어가 잠기고, 지금 이 버킷을 읽을 수 있는 주체는 전부 관리자라 좁혀서 얻는 것이 없다
# (KAN-238 에픽, KAN-242 노출 범위 표).
data "aws_iam_policy_document" "tfstate" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.tfstate.arn,
      "${aws_s3_bucket.tfstate.arn}/*",
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

resource "aws_s3_bucket_policy" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  policy = data.aws_iam_policy_document.tfstate.json

  # 같은 버킷의 퍼블릭 액세스 차단과 정책을 동시에 바꾸면 S3가 OperationAborted(진행 중인 충돌 작업)로 거절할 수
  # 있어 순서를 고정한다. 이 정책은 Deny뿐이라 퍼블릭 정책으로 판정되지 않는다.
  depends_on = [aws_s3_bucket_public_access_block.tfstate]
}

output "state_bucket" {
  value       = aws_s3_bucket.tfstate.bucket
  description = "envs/*/backend.tf가 참조하는 원격 state 버킷 이름"
}
