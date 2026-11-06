# 슬랙 알림 (KAN-273): AWS Chatbot(Amazon Q Developer in chat applications)이 SNS 토픽을 슬랙 채널로 옮긴다.
#
#   #infra-alerts  두 환경. AI ASG의 오토스케일링 확대/축소와 시작 실패(EventBridge -> Lambda scale_notify), ai-disk-high 경보(monitoring 모듈).
#   #daily-user    prod만(report_enabled). 음성 버킷을 2시간마다 세는 Lambda의 응시 현황 리포트.
#
# 이메일 경보 토픽(monitoring 모듈의 alerts)과 토픽을 나눈다 - alerts에 Chatbot을 붙이면 모든 경보가 슬랙으로 간다.
#
# Chatbot 설정은 리전 서비스가 아니라 us-east-2 등 몇 리전의 API로만 만든다(서울 없음, 2026-10-13 CLI 확인 -
# us-east-1 엔드포인트도 없다). 그래서 채널 설정만 aws.chatbot 별칭 provider로 만들고, 토픽과 규칙과 Lambda는
# 서울에 둔다. 한 채널 설정에 다른 리전 토픽을 붙일 수 있다 (Chatbot 관리자 안내서 "Add Amazon SNS topics").
#
# 채널 하나에는 계정당 Chatbot 채널 설정을 하나만 만들 수 있다 (2026-10-13 실측: 같은 채널로 두 번째를 만들면
# InvalidRequestException "has already been configured for AWS account"). 그래서 두 환경이 각자 채널 설정을 갖지 않고
# channel_configs_enabled인 환경(prod) 하나가 채널 설정을 모두 갖는다. #infra-alerts 설정에는 자기 토픽과 함께
# infra_alerts_peer_envs(staging)의 토픽을 이름 규칙으로 조립해 붙인다. 그 환경은 토픽, 규칙, 경보만 만든다.
# 피어 환경을 새로 지어 토픽이 다시 생기면 구독이 끊긴다. 토픽 ARN은 이름 규칙이라 그대로여서 이 환경의 plan은 변경 없음으로
# 나온다 - 채널 설정을 -replace로 다시 만들어야 구독이 다시 붙는다 (docs/ops/alarms.md "staging을 다시 지은 뒤", Codex 리뷰 반영).
#
# 슬랙 워크스페이스 승인(OAuth)은 Terraform이 못 한다 - 콘솔에서 사람이 한 번 하고 그 ID를 slack_team_id로 받는다
# (docs/ops/alarms.md "처음 한 번 사람이 하는 일"). 채널에 Chatbot 앱을 초대하는 것도 사람이다.

terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.chatbot]
    }
    archive = {
      source = "hashicorp/archive"
    }
  }
}

locals {
  name = "accentury-${var.env}"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  infra_alerts_topic_arns = concat(
    [aws_sns_topic.infra.arn],
    [for e in var.infra_alerts_peer_envs :
    "arn:aws:sns:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:accentury-${e}-slack-infra-alerts"],
  )
}

# ---- Chatbot 채널 역할 ----

# 채널 역할에는 정책을 붙이지 않는다 - 알림을 옮기기만 하고 슬랙에서 AWS 명령을 실행하지 않는다. 가드레일도
# 읽기 전용 하나로 좁힌다 (생략하면 계산값이 되어 콘솔 기본값인 넓은 정책이 들어갈 수 있다).
data "aws_iam_policy_document" "chatbot_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["chatbot.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "chatbot" {
  count = var.channel_configs_enabled ? 1 : 0

  name               = "${local.name}-chatbot"
  assume_role_policy = data.aws_iam_policy_document.chatbot_assume.json

  tags = { Name = "${local.name}-chatbot" }
}

locals {
  chatbot_guardrail_policy_arns = ["arn:aws:iam::aws:policy/CloudWatchReadOnlyAccess"]
}

# ---- #infra-alerts ----

resource "aws_sns_topic" "infra" {
  name         = "${local.name}-slack-infra-alerts"
  display_name = "accentury ${var.env} infra alerts"

  tags = { Name = "${local.name}-slack-infra-alerts" }
}

# 같은 계정(AWS:SourceOwner)의 publish만 허용한다 - CloudWatch 경보와 scale_notify Lambda(이 계정의 역할)가 여기에 든다.
# 기본 정책과 같은 내용이지만 명시해 둔다. 스케일 이벤트를 EventBridge가 직접 넣던 시절의 events.amazonaws.com 허용은
# Lambda로 바꾸면서 뺐다 (2026-10-13).
data "aws_iam_policy_document" "infra_topic" {
  statement {
    sid       = "SameAccountPublish"
    actions   = ["SNS:Publish"]
    resources = [aws_sns_topic.infra.arn]
    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceOwner"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "infra" {
  arn    = aws_sns_topic.infra.arn
  policy = data.aws_iam_policy_document.infra_topic.json
}

# 스케일 이벤트. Chatbot은 Auto Scaling 이벤트를 기본 형식으로 그리지 않아 사용자 지정 알림 형식(version 1.0, source custom)으로
# 바꿔야 한다. 처음에는 EventBridge 입력 변환기로 했지만, 제목에 "(현재: n대)"를 넣기로 하면서(2026-10-13, 슬랙 배너에는
# 제목만 보인다) Lambda scale_notify로 바꿨다 - 이벤트에 변경 뒤 대수가 필드로 없고(Cause 문장 안에만 있다) 변환기는
# 문장에서 숫자를 뽑지 못한다. Lambda가 ASG 희망 용량을 조회해 제목을 만들고 토픽에 넣는다. 제목 문구는 scale_notify.py의
# TITLES다. 규칙은 이벤트 종류마다 하나씩 두어 refresh 필터를 종류별로 다르게 건다.
#
# 배포의 instance refresh(이미지 배포, AMI 교체)도 시작과 종료 이벤트를 내지만 알리지 않는다 (2026-10-13 결정) -
# 배포마다 두 줄씩 쌓인다. 걸러내는 기준은 이벤트의 Cause 문구("... in response to an instance refresh ...")다.
# 시작 실패는 원인과 무관하게 전부 알린다 - instance refresh 중 실패도 사람이 봐야 한다.
locals {
  scaling_events = {
    launch = {
      detail_type     = "EC2 Instance Launch Successful"
      skip_on_refresh = true
    }
    terminate = {
      detail_type     = "EC2 Instance Terminate Successful"
      skip_on_refresh = true
    }
    launch_failed = {
      detail_type     = "EC2 Instance Launch Unsuccessful"
      skip_on_refresh = false
    }
  }
}

resource "aws_cloudwatch_event_rule" "scaling" {
  for_each = local.scaling_events

  name        = "${local.name}-ai-scaling-${replace(each.key, "_", "-")}"
  description = "${var.ai_asg_name} ${each.value.detail_type} -> #infra-alerts (KAN-273)"

  event_pattern = jsonencode({
    source      = ["aws.autoscaling"]
    detail-type = [each.value.detail_type]
    detail = merge(
      { AutoScalingGroupName = [var.ai_asg_name] },
      each.value.skip_on_refresh ? { Cause = [{ anything-but = { wildcard = "*instance refresh*" } }] } : {},
    )
  })

  tags = { Name = "${local.name}-ai-scaling-${replace(each.key, "_", "-")}" }
}

resource "aws_cloudwatch_event_target" "scaling" {
  for_each = local.scaling_events

  rule      = aws_cloudwatch_event_rule.scaling[each.key].name
  target_id = "scale-notify"
  arn       = aws_lambda_function.scale_notify.arn
}

resource "aws_lambda_permission" "scale_notify" {
  for_each = local.scaling_events

  statement_id  = "events-${replace(each.key, "_", "-")}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scale_notify.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.scaling[each.key].arn
}

data "archive_file" "scale_notify" {
  type        = "zip"
  source_file = "${path.module}/scale_notify/scale_notify.py"
  output_path = "${path.module}/.build/scale_notify.zip"
}

resource "aws_cloudwatch_log_group" "scale_notify" {
  name              = "/aws/lambda/${local.name}-scale-notify"
  retention_in_days = var.log_retention_days

  tags = { Name = "${local.name}-scale-notify" }
}

# DescribeAutoScalingGroups는 리소스 수준 권한이 없어 "*"다. 조회만이고 ASG를 바꾸는 권한은 없다.
data "aws_iam_policy_document" "scale_notify" {
  statement {
    sid       = "DescribeAsg"
    actions   = ["autoscaling:DescribeAutoScalingGroups"]
    resources = ["*"]
  }

  statement {
    sid       = "PublishInfraAlert"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.infra.arn]
  }

  statement {
    sid       = "WriteLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.scale_notify.arn}:*"]
  }
}

resource "aws_iam_role" "scale_notify" {
  name               = "${local.name}-scale-notify"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = { Name = "${local.name}-scale-notify" }
}

resource "aws_iam_role_policy" "scale_notify" {
  name   = "scale-notify"
  role   = aws_iam_role.scale_notify.id
  policy = data.aws_iam_policy_document.scale_notify.json
}

resource "aws_lambda_function" "scale_notify" {
  function_name    = "${local.name}-scale-notify"
  description      = "AI ASG 스케일 이벤트에 희망 용량을 붙여 #infra-alerts로 (KAN-273)"
  role             = aws_iam_role.scale_notify.arn
  runtime          = "python3.13"
  handler          = "scale_notify.handler"
  filename         = data.archive_file.scale_notify.output_path
  source_code_hash = data.archive_file.scale_notify.output_base64sha256
  timeout          = 30
  memory_size      = 128

  environment {
    variables = {
      SLACK_ENV = var.env
      TOPIC_ARN = aws_sns_topic.infra.arn
      ASG_NAME  = var.ai_asg_name
    }
  }

  depends_on = [aws_cloudwatch_log_group.scale_notify, aws_iam_role_policy.scale_notify]

  tags = { Name = "${local.name}-scale-notify" }
}

resource "aws_chatbot_slack_channel_configuration" "infra" {
  count    = var.channel_configs_enabled ? 1 : 0
  provider = aws.chatbot

  configuration_name    = "accentury-infra-alerts"
  iam_role_arn          = aws_iam_role.chatbot[0].arn
  slack_team_id         = var.slack_team_id
  slack_channel_id      = var.infra_alerts_channel_id
  sns_topic_arns        = local.infra_alerts_topic_arns
  guardrail_policy_arns = local.chatbot_guardrail_policy_arns
  logging_level         = "ERROR"

  tags = { Name = "accentury-infra-alerts" }
}

# ---- #daily-user (prod만) ----

resource "aws_sns_topic" "report" {
  count = var.report_enabled ? 1 : 0

  name         = "${local.name}-slack-daily-user"
  display_name = "accentury ${var.env} daily user report"

  tags = { Name = "${local.name}-slack-daily-user" }
}

resource "aws_chatbot_slack_channel_configuration" "report" {
  count    = var.report_enabled && var.channel_configs_enabled ? 1 : 0
  provider = aws.chatbot

  configuration_name    = "${local.name}-daily-user"
  iam_role_arn          = aws_iam_role.chatbot[0].arn
  slack_team_id         = var.slack_team_id
  slack_channel_id      = var.daily_user_channel_id
  sns_topic_arns        = [aws_sns_topic.report[0].arn]
  guardrail_policy_arns = local.chatbot_guardrail_policy_arns
  logging_level         = "ERROR"

  tags = { Name = "${local.name}-daily-user" }
}

data "archive_file" "report" {
  count = var.report_enabled ? 1 : 0

  type        = "zip"
  source_file = "${path.module}/voice_report/voice_report.py"
  output_path = "${path.module}/.build/voice_report.zip"
}

resource "aws_cloudwatch_log_group" "report" {
  count = var.report_enabled ? 1 : 0

  name              = "/aws/lambda/${local.name}-voice-report"
  retention_in_days = var.log_retention_days

  tags = { Name = "${local.name}-voice-report" }
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# 목록 조회만 준다 - GetObject가 없어 음성과 JSON 본문은 읽지 못한다 (KAN-273 AC). 음성 버킷 정책(bootstrap/voice.tf)은
# 본문 읽기만 허용 목록으로 막고 List는 거부하지 않으므로 bootstrap 변경 없이 이 역할만으로 된다.
data "aws_iam_policy_document" "report" {
  count = var.report_enabled ? 1 : 0

  statement {
    sid       = "ListOwnEnvPrefix"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.voice_bucket_name}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${var.env}/*"]
    }
  }

  statement {
    sid       = "PublishReport"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.report[0].arn]
  }

  statement {
    sid       = "WriteLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.report[0].arn}:*"]
  }
}

resource "aws_iam_role" "report" {
  count = var.report_enabled ? 1 : 0

  name               = "${local.name}-voice-report"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = { Name = "${local.name}-voice-report" }
}

resource "aws_iam_role_policy" "report" {
  count = var.report_enabled ? 1 : 0

  name   = "voice-report"
  role   = aws_iam_role.report[0].id
  policy = data.aws_iam_policy_document.report[0].json
}

resource "aws_lambda_function" "report" {
  count = var.report_enabled ? 1 : 0

  function_name    = "${local.name}-voice-report"
  description      = "음성 버킷 응시 현황 2시간 리포트 -> #daily-user (KAN-273)"
  role             = aws_iam_role.report[0].arn
  runtime          = "python3.13"
  handler          = "voice_report.handler"
  filename         = data.archive_file.report[0].output_path
  source_code_hash = data.archive_file.report[0].output_base64sha256
  timeout          = 120
  memory_size      = 256

  environment {
    variables = {
      REPORT_ENV   = var.env
      VOICE_BUCKET = var.voice_bucket_name
      TOPIC_ARN    = aws_sns_topic.report[0].arn
    }
  }

  depends_on = [aws_cloudwatch_log_group.report, aws_iam_role_policy.report]

  tags = { Name = "${local.name}-voice-report" }
}

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  count = var.report_enabled ? 1 : 0

  name               = "${local.name}-voice-report-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json

  tags = { Name = "${local.name}-voice-report-scheduler" }
}

resource "aws_iam_role_policy" "scheduler" {
  count = var.report_enabled ? 1 : 0

  name = "invoke-voice-report"
  role = aws_iam_role.scheduler[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.report[0].arn
    }]
  })
}

# KST 08시부터 24시(다음 날 00시)까지 짝수 정각, 하루 9번 (2026-10-13 결정). 08시 리포트가 밤사이(00~08시)를 담는다.
resource "aws_scheduler_schedule" "report" {
  count = var.report_enabled ? 1 : 0

  name                         = "${local.name}-voice-report"
  description                  = "응시 현황 리포트 (KAN-273)"
  schedule_expression          = "cron(0 0,8,10,12,14,16,18,20,22 * * ? *)"
  schedule_expression_timezone = "Asia/Seoul"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.report[0].arn
    role_arn = aws_iam_role.scheduler[0].arn
  }
}
