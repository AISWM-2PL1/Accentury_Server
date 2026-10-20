variable "env" {
  type        = string
  description = "staging 또는 prod. 이름 접두와 메시지 머리 라벨, 리포트가 세는 음성 버킷 접두에 쓴다."
}

variable "slack_team_id" {
  type        = string
  description = "AWS Chatbot 콘솔에서 승인한 슬랙 워크스페이스 ID (T로 시작). 시크릿이 아니다."
}

variable "infra_alerts_channel_id" {
  type        = string
  description = "#infra-alerts 채널 ID (C로 시작). 두 환경이 같은 채널을 쓰고 메시지 머리의 [env]로 구분한다."
}

variable "daily_user_channel_id" {
  type        = string
  description = "#daily-user 채널 ID (C로 시작). report_enabled가 false면 쓰지 않는다."
}

variable "ai_asg_name" {
  type        = string
  description = "스케일 이벤트를 받을 AI 호스트 ASG 이름 (ai-host 모듈 asg_name)."
}

variable "report_enabled" {
  type        = bool
  description = "응시 현황 2시간 리포트(Lambda, Scheduler, #daily-user 채널 설정)를 만들지. prod만 true (2026-10-13 결정)."
}

variable "voice_bucket_name" {
  type        = string
  description = "음성 전용 버킷 이름 (bootstrap/voice.tf). 리포트 Lambda가 <env>/ 접두만 나열한다."
}

variable "log_retention_days" {
  type        = number
  description = "리포트 Lambda 로그 보존 일수."
  default     = 30
}

variable "channel_configs_enabled" {
  type        = bool
  description = "Chatbot 채널 설정(과 채널 역할)을 이 환경이 가질지. 채널당 계정에 하나만 허용되므로 한 환경(prod)만 true다."
}

variable "infra_alerts_peer_envs" {
  type        = list(string)
  description = "#infra-alerts 채널 설정에 토픽을 함께 붙일 다른 환경 (channel_configs_enabled인 환경에서만 쓴다). 토픽 이름은 accentury-<env>-slack-infra-alerts 규칙이다."
  default     = []
}
