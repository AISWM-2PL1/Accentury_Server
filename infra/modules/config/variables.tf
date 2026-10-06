variable "ssm_prefix" {
  type        = string
  description = "이 환경의 SSM Parameter Store 경로 접두사 (/accentury/staging 등). compute 모듈의 EC2 역할이 이 경로만 읽는다."

  validation {
    condition     = startswith(var.ssm_prefix, "/accentury/") && !endswith(var.ssm_prefix, "/")
    error_message = "ssm_prefix는 /accentury/로 시작하고 끝에 /가 없어야 합니다 (KAN-129 경로 규약)."
  }
}

variable "domain" {
  type        = string
  description = "이 환경의 도메인 (prod = accentury.app, staging = staging.accentury.app). web-test-url과 asset-base-url에 쓴다."
}

variable "vpc_cidr" {
  type        = string
  description = "이 환경 VPC CIDR. trusted-proxies 값이다 (CloudFront VPC 오리진 ENI와 ALB가 여기 든다)."
}

variable "rds_endpoint" {
  type        = string
  description = "RDS 호스트:포트 (data 모듈 출력 endpoint)."
}

variable "db_name" {
  type        = string
  description = "데이터베이스 이름. data 모듈 aws_db_instance.db_name과 같아야 한다."
  default     = "accentury"
}

variable "rds_master_user_secret_arn" {
  type        = string
  description = "RDS 관리형 마스터 시크릿 ARN (Secrets Manager). backend가 연결 시점에 읽는다. 값은 SSM에 복사하지 않는다."
}

variable "ai_dns_name" {
  type        = string
  description = "backend가 AI를 부르는 프라이빗 DNS 이름 (network 모듈 출력 ai_dns_name, KAN-36). ACCENTURY_ANALYSIS_AIBASEURL = http://<이 값>:8000"
}

variable "analysis_ai_timeout" {
  type        = string
  description = "backend가 AI 호출에 거는 연결/읽기 타임아웃 (accentury.analysis.ai-timeout). AI 자신의 상한(ai_analysis_timeout_seconds 75초)보다 10초 길다 (KAN-172 확정 - KAN-22 임시값과 같은 값이고 staging 실증이 있다). 코드 기본값과 같다."
  default     = "85s"
}

variable "analysis_processing_timeout" {
  type        = string
  description = "실행 잔류 한도 (accentury.analysis.processing-timeout). ai-timeout x 3 + 백오프(255.9초)보다 길어야 기동이 통과한다 (AnalysisDispatchConfig). 그 위의 반올림 300초로 확정 (KAN-172). 코드 기본값과 같다."
  default     = "300s"
}

variable "analysis_dispatch_concurrency" {
  type        = number
  description = "분석 전달 워커 수 (accentury.analysis.dispatch-concurrency). AI 호스트 최대 대수(ai_max_size 3)와 같은 값이다 (KAN-272, 2026-10-06 - 그 전에는 1이라 AI를 늘려도 태스크 하나가 한 번에 1건만 보내 처리량이 늘지 않았다). AI는 호스트마다 추론을 한 번에 하나만 돌리고 8GB에서 2건이면 OOM이라(KAN-57) AI가 1대면 뒤의 호출은 AI 안에서 차례를 기다리고, 2대 이상이면 내부 ALB가 나눈다. 태스크 여러 개가 동시에 뜨면 그만큼 더 겹친다 - main.tf의 표와 README 참고. 코드 기본값과 같다."
  default     = 3

  validation {
    condition     = var.analysis_dispatch_concurrency >= 1
    error_message = "analysis_dispatch_concurrency는 1 이상이어야 합니다."
  }
}

variable "ai_analysis_timeout_seconds" {
  type        = number
  description = "AI 서버가 분석 1건에 거는 상한(초, ACCENTURY_AI_ANALYSIS_TIMEOUT_SECONDS). backend의 analysis_ai_timeout보다 짧아야 한다 (KAN-22). 단일 lock 대기와 워커 재적재 대기까지 포함하는 값이라 한 호스트에 겹친 호출 6건(태스크당 전달 워커 3개 x 롤링 배포 중 태스크 2개, KAN-272)의 6 x P95 11초 = 67초와 재적재 31초 + 추론 11초 = 42초를 덮는 75초로 확정 (KAN-172, Codex 리뷰 P1 - 짧으면 추론 중인 요청을 끊어 멀쩡한 워커를 죽이고 재전송이 새 워커를 또 죽인다). 코드 기본값과 같다."
  default     = 75
}

variable "training_bucket_name" {
  type        = string
  description = "음성 전용 S3 버킷 이름 (KAN-201, KAN-269 - bootstrap이 만든 accentury-voice-<계정 ID>). 값이 있으면 ACCENTURY_TRAINING_BUCKET과 ACCENTURY_TRAINING_KEYPREFIX 파라미터를 만들어 backend가 음성 저장에 동의한 세션의 음성 WAV와 메타 JSON을 그 버킷에 남긴다 (accentury.training.bucket). null이면 두 파라미터 자체가 없고 backend는 저장 코드를 만들지 않는다."
  default     = null
}

variable "training_key_prefix" {
  type        = string
  description = "음성 버킷 안에서 이 환경이 쓰는 키 접두사 (KAN-269). 끝에 슬래시가 없는 환경 이름(staging, prod)이고 ACCENTURY_TRAINING_KEYPREFIX의 값이 된다 (accentury.training.key-prefix). training_bucket_name이 있으면 반드시 함께 넘긴다 - fargate 모듈의 training_key_prefix와 같은 값이어야 한다."
  default     = null
}

variable "redis_host" {
  type        = string
  description = "Refresh 토큰 저장소 ElastiCache 기본 엔드포인트 호스트 (data 모듈 출력 redis_host, KAN-223)."
}

variable "redis_auth_token" {
  type        = string
  sensitive   = true
  description = "ElastiCache AUTH 토큰 (data 모듈 출력 redis_auth_token, KAN-223). SSM SecureString으로만 넘긴다."
}

# 기본값은 backend SsmPlaceholder.UNSET과 같은 문자열이다 - backend가 이 값을 "설정 전"으로 읽어 그 IdP 로그인만 막는다.
variable "auth_google_client_id" {
  type        = string
  description = "구글 서버용(웹) OAuth 클라이언트 ID (KAN-223, KAN-224). ID 토큰의 aud와 정확 일치해야 한다."
  default     = "unset-put-parameter-after-apply"
}

variable "auth_apple_bundle_id" {
  type        = string
  description = "iOS 번들 ID (KAN-223). 애플 identityToken의 aud와 정확 일치해야 한다."
  default     = "unset-put-parameter-after-apply"
}

variable "auth_kakao_app_id" {
  type        = string
  description = "카카오 앱 ID(숫자, KAN-223). access_token_info의 app_id와 일치해야 한다 - 다른 앱의 토큰을 막는 유일한 검사다."
  default     = "unset-put-parameter-after-apply"
}

variable "auth_naver_client_id" {
  type        = string
  description = "네이버 로그인 Client ID (KAN-243). backend가 SDK refresh token을 교환할 때 쓴다 - Secret은 SSM에 따로 넣는다."
  default     = "unset-put-parameter-after-apply"
}

variable "auth_apple_team_id" {
  type        = string
  description = "애플 개발자 팀 ID (KAN-241). 탈퇴 때 애플 토큰 revoke의 client_secret JWT iss다 - 키 원문은 SSM에 따로 넣는다."
  default     = "unset-put-parameter-after-apply"
}

variable "auth_apple_key_id" {
  type        = string
  description = "Sign in with Apple 키의 Key ID (KAN-241). client_secret JWT 헤더의 kid다."
  default     = "unset-put-parameter-after-apply"
}
