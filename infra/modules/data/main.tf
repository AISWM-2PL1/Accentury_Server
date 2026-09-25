# RDS PostgreSQL 프로비저닝 (KAN-122)과 Refresh 토큰 저장소 ElastiCache Redis (KAN-223).
#
# PostgreSQL 16: backend/docker-compose.yml의 로컬 버전(postgres:16)과 동일 메이저.
# 단일 AZ, 퍼블릭 액세스 차단, 사설 서브넷 배치. 다중 AZ는 프로토타입 범위 외.

terraform {
  required_providers {
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

locals {
  name = "accentury-${var.env}"
}

# 최종 스냅샷 이름에 붙는 고유 접미사. 이름이 고정이면 스택을 재구축한 뒤 두 번째
# destroy가 기존 스냅샷과의 이름 충돌로 거부된다. timestamp()는 매 plan에 값이
# 바뀌어 "plan 변경 없음" AC를 깨므로 state에 고정되는 random_id를 쓴다 -
# 스택 incarnation마다 새 값, 한 incarnation 안에서는 불변.
resource "random_id" "final_snapshot" {
  count = var.skip_final_snapshot ? 0 : 1

  byte_length = 4
}

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-rds"
  subnet_ids = var.private_subnet_ids

  tags = { Name = "${local.name}-rds" }
}

resource "aws_db_instance" "this" {
  identifier = local.name

  engine = "postgres"
  # 메이저 버전만 고정한다. 마이너는 자동 업그레이드(auto_minor_version_upgrade
  # 기본 true)에 맡기고, provider가 메이저 접두사 일치로 plan 드리프트를 내지 않는다.
  engine_version = "16"

  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "accentury"
  username = "accentury"
  # 마스터 비밀번호는 RDS가 생성해 Secrets Manager에 보관한다 (KAN-122의
  # "Secrets Manager 또는 SSM SecureString" 중 전자). 코드, tfvars, state 어디에도
  # 평문 비밀번호가 존재하지 않고, 환경별 시크릿이 자동으로 분리된다.
  manage_master_user_password = true

  multi_az               = false
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.rds_sg_id]

  backup_retention_period = 7

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${local.name}-final-${random_id.final_snapshot[0].hex}"

  tags = { Name = local.name }
}

# ---- Refresh 토큰 저장소 (KAN-223) ----
#
# Redis는 앱 계정 인증의 Refresh 토큰에만 쓴다 (명세서 §2.1) - 세션, 요청 제한, 분석 작업은 여전히 RDS와 태스크
# 메모리다. 그래서 작게 간다: 단일 노드 cache.t4g.micro, 복제본과 자동 장애 조치 없음, 스냅샷 없음. Redis가 죽으면
# 로그인과 refresh만 503이고(AUTH_STORE_UNAVAILABLE) 익명 응시는 영향이 없다 (NFR-AV-02). 노드를 새로 만들면 저장된
# Refresh가 전부 사라져 앱 사용자가 한 번씩 다시 로그인한다 - 그 이상의 손실은 없어 스냅샷을 두지 않는다.
#
# 사설 서브넷(RDS와 같은 서브넷 그룹 대역)에 두고, redis-sg가 backend-sg의 6379만 받는다. 전송 암호화(TLS)와 저장
# 암호화를 켜고 AUTH 토큰을 건다 - 전송 암호화가 켜져 있어야 AUTH 토큰을 걸 수 있다. backend는 deploy 프로파일에서
# TLS로 붙는다 (application-deploy.yml spring.data.redis.ssl.enabled).

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${local.name}-redis"
  subnet_ids = var.private_subnet_ids

  tags = { Name = "${local.name}-redis" }
}

# AUTH 토큰. ElastiCache 규칙(16~128자, 출력 가능한 ASCII 중 @ " / 공백 제외)에 맞게 영숫자만 쓴다. state에 평문이
# 남는다 - 관리자 토큰과 같은 수용 범위다 (S3 암호화 + 버전 관리 버킷, KAN-140). backend에는 config 모듈이 SSM
# SecureString SPRING_DATA_REDIS_PASSWORD로 넘긴다. 재발급은 envs 루트에서
# `terraform apply -replace='module.data.random_password.redis_auth_token'` 뒤 backend 태스크를 새로 띄운다 -
# 갱신 전략이 ROTATE라 apply 동안 옛 토큰도 받으므로 떠 있는 태스크가 끊기지 않는다.
resource "random_password" "redis_auth_token" {
  length  = 48
  special = false
}

# 메모리가 차도 키를 내쫓지 않는다 (Codex 리뷰 P1). 기본 파라미터 그룹(default.redis7)은 volatile-lru라 TTL이 있는
# 키를 내쫓는데, 여기 키는 전부 TTL이 있다 - 패밀리 집합(rtfam)만 먼저 내쫓기면 로그아웃과 재사용 감지가 토큰을 못
# 찾아 폐기된 줄 알았던 Refresh가 살아남는다. noeviction이면 가득 찼을 때 쓰기가 오류로 떨어지고, backend는 그것을
# 503(AUTH_STORE_UNAVAILABLE)으로 돌려준다 - 로그인이 막히는 쪽이 폐기가 새는 쪽보다 안전하다. backend의 Lua 스크립트도
# 패밀리 집합이 없으면 그 토큰을 무효로 본다 (RefreshTokens, 이중 방어).
resource "aws_elasticache_parameter_group" "redis" {
  name   = "${local.name}-redis7"
  family = "redis7"

  parameter {
    name  = "maxmemory-policy"
    value = "noeviction"
  }
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${local.name}-redis"
  description          = "Accentury ${var.env} refresh token store (KAN-223)"

  engine               = "redis"
  engine_version       = "7.1"
  parameter_group_name = aws_elasticache_parameter_group.redis.name
  node_type            = var.redis_node_type
  num_cache_clusters   = 1
  port                 = 6379

  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [var.redis_sg_id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth_token.result
  auth_token_update_strategy = "ROTATE"

  snapshot_retention_limit = 0
  apply_immediately        = true

  tags = { Name = "${local.name}-redis" }
}
