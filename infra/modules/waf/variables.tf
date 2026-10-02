variable "env" {
  type        = string
  description = "환경 이름 (staging | prod)"
}

variable "enforce" {
  type        = bool
  description = "true면 규칙이 차단(Block)한다. false면 전부 Count(기록만). 티켓 절차대로 false로 시작해 로그를 관찰한 뒤 true로 바꾼다."
}

variable "rate_limit" {
  type        = number
  description = "IP당 5분 창에서 허용하는 세션 생성 + 음성 업로드 요청 수. 초과분은 429(enforce) 또는 Count."

  validation {
    condition     = var.rate_limit >= 10
    error_message = "rate_limit은 AWS WAF 최소값 10 이상이어야 합니다."
  }
}

variable "auth_rate_limit" {
  type        = number
  description = "IP당 5분 창에서 허용하는 로그인 + refresh POST 수 (KAN-244). 초과분은 429(enforce) 또는 Count."

  validation {
    condition     = var.auth_rate_limit >= 10
    error_message = "auth_rate_limit은 AWS WAF 최소값 10 이상이어야 합니다."
  }
}

variable "admin_rate_limit" {
  type        = number
  description = "IP당 5분 창에서 허용하는 관리자 경로(/admin/) 요청 수 (KAN-244). 초과분은 429(enforce) 또는 Count."

  validation {
    condition     = var.admin_rate_limit >= 10
    error_message = "admin_rate_limit은 AWS WAF 최소값 10 이상이어야 합니다."
  }
}

variable "ip_reputation_enforce" {
  type        = bool
  description = "IP 평판 관리형 규칙을 차단으로 돌릴지 (KAN-244). enforce와 둘 다 true여야 차단한다. false면 이 규칙만 Count."
}

variable "log_retention_days" {
  type        = number
  description = "WAF 로그 보존 일수. 오탐 판정에는 며칠이면 충분하고, 길게 두면 비용만 든다."
  default     = 7
}
