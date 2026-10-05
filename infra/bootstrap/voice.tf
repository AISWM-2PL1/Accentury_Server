# 음성 전용 S3 버킷 (KAN-269). 두 환경이 함께 쓰는 계정 단위 리소스라 bootstrap에 둔다.
#
# 이 전에는 환경 스택(envs/staging)이 accentury-staging-training 버킷을 force_destroy로 들고 있어서, 환경을 부수거나
# 스위치를 끄는 apply 한 번에 모인 음성이 통째로 사라질 수 있었다. 음성은 다시 만들 수 없는 데이터라 환경의 수명과
# 떼어 낸다 - 환경 teardown이 이 버킷을 건드리지 못하고, 이 스택에서도 prevent_destroy로 막는다.
#   - 키 접두사로 환경을 나눈다: staging/..., prod/... backend 태스크 역할은 자기 환경 접두사 아래로만 쓴다
#     (modules/fargate PutTrainingSamples).
#   - 저장 대상은 음성 저장(선택 동의)에 동의한 세션뿐이다. 그 판정은 backend가 한다.
#   - 버전 관리를 켠다. 같은 키로 덮어쓰거나 지워도 이전 버전이 남는다.
#   - 수명주기(만료) 규칙은 두지 않는다. 음성은 Terraform이든 S3든 자동으로 지우지 않고, 지우는 판단은 사람이 한다.
#   - force_destroy도 없다. 비어 있지 않은 버킷은 destroy가 실패한다.
#
# 적용 순서: 이 스택(bootstrap)이 먼저다. 버킷이 생긴 뒤에 envs/staging, envs/prod를 apply해야 backend가 없는 버킷에
# 쓰려다 실패하지 않는다 (README "음성 전용 S3" 절).

variable "voice_reader_principal_arns" {
  type        = list(string)
  description = "음성 버킷의 객체 본문(GetObject, GetObjectVersion)을 읽을 수 있는 IAM 주체 ARN 목록 (KAN-269). 이 목록 밖의 주체는 관리자 자격 증명이어도 버킷 정책이 거부한다. null이면 학습 담당 IAM 사용자 jaeyoung, 학습용 EC2 역할 accentury-track2-ec2-role, 운영 담당 IAM 사용자 accentury-cli다. 역할을 맡은 세션의 aws:PrincipalArn은 역할 ARN이라 역할은 역할 ARN으로 적는다."
  default     = null
}

locals {
  voice_reader_principal_arns = coalesce(var.voice_reader_principal_arns, [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/jaeyoung",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/accentury-track2-ec2-role",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/accentury-cli",
  ])
}

resource "aws_s3_bucket" "voice" {
  bucket = "accentury-voice-${data.aws_caller_identity.current.account_id}"

  # 음성은 어떤 이유로도 Terraform이 지우지 않는다. 이 블록을 지우거나 스택을 destroy하려 하면 plan이 실패한다.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "voice" {
  bucket = aws_s3_bucket.voice.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "voice" {
  bucket = aws_s3_bucket.voice.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "voice" {
  bucket = aws_s3_bucket.voice.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 버킷 정책. 두 문장 다 Deny라 IAM 쪽 허용이 넓어도 이긴다.
# 1) TLS가 아닌 요청은 전부 거부한다 (tfstate, audit 버킷과 같은 문장).
# 2) 객체 본문 읽기(GetObject, GetObjectVersion)는 voice_reader_principal_arns의 주체만 한다. 목록 밖이면 관리자
#    자격 증명도 거부된다. 이 문장은 허용이 아니라 거부의 예외일 뿐이라, 목록의 주체도 자기 IAM 정책에 s3:GetObject 허용이 따로
#    있어야 읽는다.
# List, Put, Delete는 여기서 거부하지 않는다. HeadBucket이 s3:ListBucket 권한으로 판정되므로 List를 거부하면
# Terraform의 버킷 refresh가 막혀 이후 plan과 apply가 잠긴다 (KAN-239 리뷰에서 확인). 쓰기는 backend 태스크 역할의
# IAM 정책이 자기 환경 접두사로 좁힌다.
data "aws_iam_policy_document" "voice" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.voice.arn,
      "${aws_s3_bucket.voice.arn}/*",
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
    sid       = "ObjectReadOnlyByVoiceReaders"
    effect    = "Deny"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${aws_s3_bucket.voice.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    # 값이 여러 개면 어느 것과도 같지 않을 때만 거부된다 (부정 연산자의 다중 값은 NOR).
    condition {
      test     = "ArnNotEquals"
      variable = "aws:PrincipalArn"
      values   = local.voice_reader_principal_arns
    }
  }
}

resource "aws_s3_bucket_policy" "voice" {
  bucket = aws_s3_bucket.voice.id
  policy = data.aws_iam_policy_document.voice.json

  # 퍼블릭 액세스 차단과 정책을 동시에 바꾸면 S3가 OperationAborted로 거절할 수 있다 (tfstate 버킷과 같은 이유).
  depends_on = [aws_s3_bucket_public_access_block.voice]
}

output "voice_bucket" {
  value       = aws_s3_bucket.voice.bucket
  description = "음성 전용 S3 버킷 (KAN-269). 두 환경이 staging/, prod/ 접두사로 나눠 쓴다. envs는 이 출력을 읽지 않고 같은 규칙(accentury-voice-<계정 ID>)으로 이름을 조립한다."
}
