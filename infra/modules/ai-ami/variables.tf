variable "env" {
  type        = string
  description = "이 파이프라인을 소유한 환경 이름 (prod). 리소스 이름 접두사(accentury-{env}-ai-ami)에만 쓰고, 만들어지는 AMI는 두 환경이 함께 쓴다 (KAN-285)."
}

variable "vpc_id" {
  type        = string
  description = "빌드 인스턴스의 보안 그룹을 만들 VPC (network 모듈 vpc_id)."
}

variable "subnet_id" {
  type        = string
  description = "빌드 인스턴스를 띄울 퍼블릭 서브넷 하나 (network 모듈 public_subnet_ids[0]). 계정에 기본 VPC가 없어 Image Builder가 서브넷을 고르지 못하므로 명시한다. 퍼블릭인 이유는 ai 호스트와 같다 - 사설 서브넷에는 NAT가 없어 dnf, GitHub, ECR, SSM에 닿지 못한다."
}

variable "instance_type" {
  type        = string
  description = "빌드 인스턴스 타입. ai 호스트와 같은 c7i.xlarge다 - 압축 4.19GB 이미지를 푸는 pull이 CPU 작업이라 작은 타입은 빌드만 길어지고, 호스트와 같은 타입이면 docker 저장소 배치가 운영과 같다."
}

variable "ssm_prefix" {
  type        = string
  description = "이 환경의 SSM Parameter Store 경로 접두사 (/accentury/prod). 빌드가 {ssm_prefix}/IMAGE_TAG를 읽어 그 순간의 활성 태그를 AMI에 담는다 - 어느 태그를 구울지 tfvars에 적지 않는다 (KAN-285 결정, prod 활성 태그 하나를 두 환경이 공용)."

  validation {
    condition     = startswith(var.ssm_prefix, "/accentury/")
    error_message = "ssm_prefix는 /accentury/로 시작해야 합니다 (KAN-129 경로 규약)."
  }
}

variable "ecr_repository" {
  type        = string
  description = "AMI에 담을 이미지의 ECR 리포지토리 이름. ai 호스트 compose(docker-compose.ai.yml)의 image와 같아야 docker가 캐시로 인식한다."
  default     = "accentury/ai"
}

variable "compose_version" {
  type        = string
  description = "AMI에 깔 docker compose 플러그인 버전. ai-host 모듈 compose_version과 같아야 한다 - 다르면 user_data가 첫 부팅에 그 버전을 다시 받는다 (user_data.sh.tftpl의 버전 검사)."
  default     = "v2.39.1"
}

variable "root_volume_size" {
  type        = number
  description = "빌드 인스턴스와 AMI 스냅샷의 루트 볼륨 GiB. OS 약 2.5GB + 이미지 약 7GB + pull 임시 공간이 들어가면 된다. ai 호스트 시작 템플릿의 40GiB(ai_root_volume_size)보다 작아야 한다 - AMI 스냅샷보다 작은 볼륨으로는 인스턴스를 띄울 수 없다."
  default     = 20
}

variable "recipe_version" {
  type        = string
  description = "레시피와 컴포넌트의 버전 (semver). component.yml이나 레시피 인자(인스턴스 타입, 볼륨, compose 버전)를 바꾸면 올린다 - Image Builder는 같은 이름과 버전의 레시피를 다시 만들지 못하고, Terraform은 레시피 속성 대부분을 교체로 처리한다. 이미지 태그는 빌드 시점에 SSM에서 읽으므로 태그가 바뀌어도 올리지 않는다."
  default     = "1.0.0"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.recipe_version))
    error_message = "recipe_version은 x.y.z 형식이어야 합니다."
  }
}
