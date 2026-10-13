output "pipeline_arn" {
  value       = aws_imagebuilder_image_pipeline.ai.arn
  description = "AMI 빌드 파이프라인 ARN (KAN-285). 수동 실행: aws imagebuilder start-image-pipeline-execution --image-pipeline-arn <이 값>. 최근 AMI: aws imagebuilder list-image-pipeline-images --image-pipeline-arn <이 값>"
}

output "pipeline_name" {
  value       = aws_imagebuilder_image_pipeline.ai.name
  description = "파이프라인 이름 (accentury-{env}-ai-ami). 콘솔 EC2 Image Builder > Image pipelines에서 이 이름으로 찾는다."
}

output "image_log_group_name" {
  value       = "/aws/imagebuilder/${aws_imagebuilder_image_recipe.ai.name}"
  description = "빌드 로그 그룹. Image Builder가 레시피 이름으로 만든다 - 빌드가 실패하면 여기부터 본다: aws logs tail <이 값> --since 1h"
}
