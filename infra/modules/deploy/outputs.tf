output "github_deploy_role_arn" {
  value       = aws_iam_role.github_deploy.arn
  description = "Accentury_Server 레포의 GitHub environment 변수 AWS_DEPLOY_ROLE_ARN 값 (KAN-127). 이미지 배포와 스모크"
}

output "github_web_deploy_role_arn" {
  value       = aws_iam_role.github_web_deploy.arn
  description = "Accentury_App 레포의 GitHub environment 변수 AWS_DEPLOY_ROLE_ARN 값 (KAN-306). 웹 번들 업로드와 캐시 무효화만"
}
