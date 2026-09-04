output "payment_repo_url" {
  description = "ECR payment-service repository URL"
  value       = aws_ecr_repository.payment.repository_url
}

output "ledger_repo_url" {
  description = "ECR ledger-service repository URL"
  value       = aws_ecr_repository.ledger.repository_url
}

output "loan_repo_url" {
  description = "ECR loan-service repository URL"
  value       = aws_ecr_repository.loan.repository_url
}

output "payment_repo_name" {
  description = "ECR payment-service repository name"
  value       = aws_ecr_repository.payment.name
}

output "ledger_repo_name" {
  description = "ECR ledger-service repository name"
  value       = aws_ecr_repository.ledger.name
}

output "loan_repo_name" {
  description = "ECR loan-service repository name"
  value       = aws_ecr_repository.loan.name
}