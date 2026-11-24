output "alb_dns_name" {
  description = "Public URL of the application (HTTP)"
  value       = aws_lb.main.dns_name
}

output "github_deploy_role_arn" {
  description = "Value of the GitHub variable AWS_ROLE_ARN"
  value       = aws_iam_role.github_deploy.arn
}
