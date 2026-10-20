provider "aws" {
  region = var.region

  # 모든 생성 리소스에 env 태그를 일괄 적용한다 (KAN-140, KAN-35 비용 태그 AC).
  default_tags {
    tags = {
      env        = var.env
      project    = "accentury"
      managed-by = "terraform"
    }
  }
}

# CloudFront는 us-east-1 인증서만 읽는다 (KAN-119). 인증서 data 조회 전용.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      env        = var.env
      project    = "accentury"
      managed-by = "terraform"
    }
  }
}

# AWS Chatbot 채널 설정 전용 (KAN-273). Chatbot API는 서울과 us-east-1에 없고 us-east-2 등 몇 리전에만 있다
# (2026-10-13 CLI 확인). 채널 설정만 여기서 만들고 거기 붙는 SNS 토픽은 서울에 둔다.
provider "aws" {
  alias  = "chatbot"
  region = "us-east-2"

  default_tags {
    tags = {
      env        = var.env
      project    = "accentury"
      managed-by = "terraform"
    }
  }
}
