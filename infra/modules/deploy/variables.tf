variable "env" {
  type        = string
  description = "환경 이름 (staging | prod). GitHub environment 이름과 같아야 한다 - 신뢰 정책의 sub 조건이 이 값에 묶인다."
}

variable "github_owner" {
  type        = string
  description = "배포 워크플로가 도는 GitHub 저장소들의 소유자(조직) 이름"
}

variable "github_owner_id" {
  type        = number
  description = "저장소 소유자(조직)의 숫자 ID. 불변 subject claim(repo:OWNER@ID/REPO@ID:...)에 들어간다."
}

variable "github_server_repository" {
  type = object({
    name = string # 저장소 이름 (소유자 제외)
    id   = number # 저장소의 숫자 ID. 불변 subject claim에 들어간다.
  })
  description = "이미지 배포 역할(github-deploy)을 맡는 저장소 - Accentury_Server (KAN-221 레포 분리, KAN-306 역할 분리). 이미지 배포와 스모크가 여기서 돈다. 다른 저장소는 environment 이름이 같아도 AssumeRoleWithWebIdentity가 거부된다."
}

variable "github_app_repository" {
  type = object({
    name = string # 저장소 이름 (소유자 제외)
    id   = number # 저장소의 숫자 ID. 불변 subject claim에 들어간다.
  })
  description = "웹 배포 역할(github-web-deploy)을 맡는 저장소 - Accentury_App (KAN-306). 웹 번들 업로드와 캐시 무효화만 할 수 있고 이미지 배포 역할은 맡을 수 없다."
}

variable "web_bucket_arn" {
  type        = string
  description = "웹 번들 버킷 ARN (edge 모듈 출력). 업로드 권한을 이 버킷 하나로 한정한다."
}

variable "ssm_prefix" {
  type        = string
  description = "이 환경의 SSM 경로 접두사 (/accentury/{env}). IMAGE_TAG 쓰기와 관리자 토큰 읽기를 이 경로로 한정한다 (KAN-128)."
}

variable "ci_image_push" {
  type        = bool
  description = "ECR push 권한 (KAN-128 승격 모델). staging만 true - prod로 가는 이미지는 언제나 staging을 거친 것이어야 한다."
}

variable "cloudfront_distribution_arn" {
  type        = string
  description = "이 환경의 CloudFront 배포 ARN (edge 모듈 출력). 무효화 권한을 이 배포 하나로 한정한다."
}

variable "ecs" {
  type = object({
    cluster_arn                = string # ecs:cluster 조건 값 (태스크 조회)
    service_arn                = string # UpdateService, DescribeServices 리소스
    task_definition_family_arn = string # RegisterTaskDefinition 리소스 (리비전 없는 패밀리 ARN)
    task_role_arn              = string # RegisterTaskDefinition의 PassRole 대상
    execution_role_arn         = string # RegisterTaskDefinition의 PassRole 대상
  })
  description = "backend Fargate 서비스 (KAN-165, fargate 모듈 출력). 파이프라인이 리비전 등록과 update-service를 이 서비스에만 할 수 있게 한정한다."
}
