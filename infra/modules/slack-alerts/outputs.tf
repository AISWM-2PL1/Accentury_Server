output "infra_alerts_topic_arn" {
  value       = aws_sns_topic.infra.arn
  description = "#infra-alerts로 가는 SNS 토픽. 경보를 슬랙에도 보내려면 그 경보의 alarm_actions와 ok_actions에 더한다 (지금은 ai-disk-high만)."
}

output "report_function_name" {
  value       = try(aws_lambda_function.report[0].function_name, null)
  description = "응시 현황 리포트 Lambda. 손으로 한 번 돌려 보려면 aws lambda invoke --function-name <이 값> /dev/stdout. report_enabled가 false면 null."
}
