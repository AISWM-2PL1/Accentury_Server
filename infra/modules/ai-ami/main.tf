# AI 호스트 AMI 사전 적재 파이프라인 (KAN-285). EC2 Image Builder가 최신 AL2023 위에 docker, compose 플러그인,
# 그 순간의 활성 accentury/ai 이미지를 깔아 AMI로 남긴다. ai 호스트의 첫 부팅(2026-10-14 prod 실측 3분 22초)에서
# docker 설치 40초와 이미지 pull 1분 53초가 매번 같은 일이라 미리 해 두는 것이다. 구운 AMI는 두 환경 tfvars
# ai_ami_id에 적어 쓴다 (README "AI 호스트 AMI 갱신") - 파이프라인은 AMI ID를 Terraform에 돌려주지 않는다.
# 돌려받아 자동으로 쓰면 빌드가 끝날 때마다 손대지 않은 plan에 인스턴스 교체가 끼어들기 때문이다 (KAN-246과 같은 이유).
#
# 파이프라인은 prod 스택이 가진다 (tfvars ai_ami_pipeline_enabled). 계정에 기본 VPC가 없어 빌드 인스턴스를 띄울
# 서브넷이 필요한데, 환경 스택 밖(bootstrap)에는 VPC가 없다. 만든 AMI는 계정 자원이라 staging을 부수고 다시 지어도
# 남는다. 일정(schedule)은 없다 - 수동 실행이다. 모델 가중치 레이어가 태그 간에 공유돼(2026-10-14 실측, 4.19GB 중
# 큰 레이어 전부 같고 코드 쪽 약 66MB만 다르다) 배포마다 굽지 않아도 pull이 그 66MB로 끝난다. 다시 굽는 때는
# 월 1회 OS 갱신(KAN-246)과 모델 가중치가 바뀐 뒤다.

locals {
  name = "accentury-${var.env}-ai-ami"

  # 레시피의 부모 이미지. SSM 퍼블릭 파라미터 참조(ssm: 접두사)라 빌드할 때마다 그 순간의 최신 AL2023 AMI로 풀린다 -
  # KAN-246의 월 1회 OS 갱신이 "파이프라인 한 번 돌리고 ai_ami_id 바꾸기"가 된다. 레시피 속성이 아니라 빌드마다
  # 풀리는 값이므로 plan에는 나타나지 않는다. KAN-246이 수동 절차에서 읽던 것과 같은 파라미터다.
  # Image Builder 관리형 이미지 amazon-linux-2023-x86-latest/x.x.x는 이 리전에서 DOCKER 형식뿐이라(2026-10-14 실측,
  # CreateImageRecipe가 "must be one of the following type(s): AMI"로 거절) 쓰지 않는다.
  parent_image = "ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"

  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.region}.amazonaws.com"

  image_tag_parameter = "${var.ssm_prefix}/IMAGE_TAG"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ---- 빌드 인스턴스 IAM ----

# Image Builder는 빌드 인스턴스에 SSM Run Command로 컴포넌트를 내려보낸다 - SSM 코어와 Image Builder 관리형 정책이
# 필수다. 그 위에 이미지를 당길 ECR 읽기와 활성 태그를 읽을 IMAGE_TAG 파라미터 하나만 더한다. ai 호스트 역할
# (ai-host 모듈)과 달리 /ai 하위 경로(내부 호출 토큰)는 읽지 않는다 - AMI에 비밀이 들어갈 길을 두지 않는다.
data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "build" {
  name               = "${local.name}-build"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.build.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "imagebuilder" {
  role       = aws_iam_role.build.name
  policy_arn = "arn:aws:iam::aws:policy/EC2InstanceProfileForImageBuilder"
}

resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.build.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

locals {
  image_tag_parameter_arn = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${local.image_tag_parameter}"
}

data "aws_iam_policy_document" "build" {
  statement {
    sid       = "ReadActiveImageTag"
    actions   = ["ssm:GetParameter"]
    resources = [local.image_tag_parameter_arn]
  }

  # AmazonSSMManagedInstanceCore가 ssm:GetParameter와 GetParameters를 리소스 *에 허용한다 (2026-10-14 정책 문서 확인,
  # Codex 리뷰 P2). 위 Allow만으로는 경계가 서지 않는다 - 빌드 인스턴스에서 도는 코드가 두 환경의 내부 호출 토큰,
  # 관리자 토큰, JWT 시크릿 같은 /accentury/* 파라미터를 전부 읽을 수 있다. Deny는 Allow를 이기므로 IMAGE_TAG 하나를
  # 뺀 모든 파라미터 읽기를 막는다. SSM 에이전트와 Image Builder 워크플로는 파라미터를 읽지 않으므로(에이전트는
  # GetManifest, GetDocument만) 빌드에 영향이 없다 - 있다면 빌드가 AccessDenied로 시끄럽게 실패한다.
  statement {
    sid    = "DenyOtherParameters"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:GetParameterHistory",
    ]
    not_resources = [local.image_tag_parameter_arn]
  }
}

resource "aws_iam_role_policy" "build" {
  name   = "ai-ami-build-access"
  role   = aws_iam_role.build.id
  policy = data.aws_iam_policy_document.build.json
}

resource "aws_iam_instance_profile" "build" {
  name = "${local.name}-build"
  role = aws_iam_role.build.name
}

# ---- 빌드 인스턴스 SG: 인바운드 없음, 아웃바운드 HTTPS만 ----

# dnf(cdn.amazonlinux.com), GitHub(compose 플러그인), ECR, SSM이 전부 443이다. 인바운드는 없다 - 접속은 SSM이다.
resource "aws_security_group" "build" {
  name        = "${local.name}-build-sg"
  description = "Image Builder build instance for the AI host AMI - no inbound, HTTPS outbound only"
  vpc_id      = var.vpc_id

  tags = { Name = "${local.name}-build-sg" }
}

resource "aws_vpc_security_group_egress_rule" "build_https_ipv4" {
  security_group_id = aws_security_group.build.id
  description       = "HTTPS to dnf mirrors, GitHub, ECR, SSM"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "build_https_ipv6" {
  security_group_id = aws_security_group.build.id
  description       = "HTTPS to dnf mirrors, GitHub, ECR, SSM"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv6         = "::/0"
}

# ---- 컴포넌트, 레시피, 인프라, 배포, 파이프라인 ----

# 컴포넌트 본문은 component.yml이다. 인자(레지스트리, 리포지토리, 태그 파라미터, compose 버전, 리전)는 레시피가
# 넘긴다. 본문이나 인자를 바꾸면 version을 올려야 한다 (variables.tf recipe_version) - Image Builder는 같은 이름과
# 버전을 다시 만들지 못한다.
resource "aws_imagebuilder_component" "prebake" {
  name        = "${local.name}-prebake"
  description = "docker, compose plugin, active accentury/ai image (KAN-285)"
  platform    = "Linux"
  version     = var.recipe_version
  data        = file("${path.module}/component.yml")

  supported_os_versions = ["Amazon Linux 2023"]
}

resource "aws_imagebuilder_image_recipe" "ai" {
  name         = local.name
  description  = "AL2023 + docker + compose + active accentury/ai image for the AI host ASG (KAN-285)"
  version      = var.recipe_version
  parent_image = local.parent_image

  component {
    component_arn = aws_imagebuilder_component.prebake.arn

    parameter {
      name  = "Registry"
      value = local.ecr_registry
    }

    parameter {
      name  = "Repository"
      value = var.ecr_repository
    }

    parameter {
      name  = "ImageTagParameter"
      value = local.image_tag_parameter
    }

    parameter {
      name  = "ComposeVersion"
      value = var.compose_version
    }

    parameter {
      name  = "Region"
      value = data.aws_region.current.region
    }
  }

  # AL2023 루트 디바이스. 암호화는 ai 호스트 시작 템플릿과 같다. 크기는 호스트(40GiB)보다 작아야 한다 - 스냅샷보다
  # 작은 볼륨으로는 인스턴스를 띄울 수 없고, 호스트 쪽은 두 이미지 공존과 pull 임시 공간까지 센 값이다.
  block_device_mapping {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.root_volume_size
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  # 호스트는 SSM Session Manager로 접속하므로(KAN-124) 에이전트를 남긴다.
  systems_manager_agent {
    uninstall_after_build = false
  }
}

resource "aws_imagebuilder_infrastructure_configuration" "ai" {
  name                  = local.name
  description           = "build instance for the AI host AMI (KAN-285)"
  instance_profile_name = aws_iam_instance_profile.build.name
  instance_types        = [var.instance_type]
  subnet_id             = var.subnet_id
  security_group_ids    = [aws_security_group.build.id]

  # 실패한 빌드 인스턴스를 남기지 않는다 - 원인은 로그 그룹(/aws/imagebuilder/<레시피 이름>)에 있다.
  terminate_instance_on_failure = true

  instance_metadata_options {
    http_tokens = "required"
  }

  # 빌드 인스턴스와 그 볼륨의 태그. 프로바이더 default_tags는 Image Builder가 띄우는 인스턴스에 전파되지 않는다
  # (ai-host 시작 템플릿의 tag_specifications와 같은 이유, KAN-35 비용 태그). Name은 예약 키라 못 넣는다
  # (CreateInfrastructureConfiguration이 거절, 2026-10-14) - Image Builder가 "EC2 Image Builder Build Instance"를 붙인다.
  resource_tags = {
    env        = var.env
    project    = "accentury"
    managed-by = "terraform"
  }
}

resource "aws_imagebuilder_distribution_configuration" "ai" {
  name        = local.name
  description = "AMI for the AI host ASG, shared by staging and prod (KAN-285)"

  distribution {
    region = data.aws_region.current.region

    ami_distribution_configuration {
      # 이름의 날짜가 곧 빌드 시각이다 - tfvars ai_ami_id 옆 주석에 이 이름을 적는다 (KAN-246 절차와 같다).
      name = "accentury-ai-host-{{ imagebuilder:buildDate }}"

      # AMI는 두 환경이 함께 쓰는 계정 자원이라 env 태그가 shared다 (bootstrap 스택과 같은 규약, KAN-35 비용 태그).
      ami_tags = {
        Name           = "accentury-ai-host"
        env            = "shared"
        project        = "accentury"
        managed-by     = "terraform"
        accentury-role = "ai-host-ami"
      }
    }
  }
}

resource "aws_imagebuilder_image_pipeline" "ai" {
  name                             = local.name
  description                      = "manual: bake docker + compose + active accentury/ai into an AL2023 AMI (KAN-285)"
  image_recipe_arn                 = aws_imagebuilder_image_recipe.ai.arn
  infrastructure_configuration_arn = aws_imagebuilder_infrastructure_configuration.ai.arn
  distribution_configuration_arn   = aws_imagebuilder_distribution_configuration.ai.arn
  status                           = "ENABLED"

  # 이미지 테스트는 AMI로 인스턴스를 하나 더 띄워 SSM 연결만 본다 - 그 확인은 staging instance refresh가 대신한다.
  # 끄면 빌드가 약 5분 짧다.
  image_tests_configuration {
    image_tests_enabled = false
  }

  # 일정 없음 - README "AI 호스트 AMI 갱신"의 수동 실행이다.

  # provider 5.74.0부터 필수다 - 없으면 레시피 교체 때 파이프라인이 옛 레시피를 붙든 채 destroy가 의존성 오류로 멈춘다.
  lifecycle {
    replace_triggered_by = [aws_imagebuilder_image_recipe.ai]
  }
}
