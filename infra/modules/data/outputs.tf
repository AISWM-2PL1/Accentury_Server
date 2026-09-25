output "endpoint" {
  value       = aws_db_instance.this.endpoint
  description = "호스트:포트 (KAN-129 spring.datasource 주입용)"
}

output "address" {
  value = aws_db_instance.this.address
}

output "master_user_secret_arn" {
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
  description = "RDS 관리 마스터 자격 증명이 담긴 Secrets Manager 시크릿"
}

output "instance_identifier" {
  value       = aws_db_instance.this.identifier
  description = "CloudWatch AWS/RDS의 DBInstanceIdentifier 차원 값 (KAN-134)"
}

output "redis_host" {
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
  description = "Refresh 토큰 저장소의 기본 엔드포인트 호스트 (KAN-223). config 모듈이 SPRING_DATA_REDIS_HOST로 넘긴다."
}

output "redis_auth_token" {
  value       = random_password.redis_auth_token.result
  sensitive   = true
  description = "ElastiCache AUTH 토큰 (KAN-223). config 모듈이 SPRING_DATA_REDIS_PASSWORD(SecureString)로 넘긴다."
}

output "redis_replication_group_id" {
  value       = aws_elasticache_replication_group.redis.id
  description = "CloudWatch AWS/ElastiCache 지표의 차원 재료 (KAN-223)"
}
