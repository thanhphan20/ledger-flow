output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = module.ecs.alb_dns_name
}

output "alb_zone_id" {
  description = "ALB zone ID"
  value       = module.ecs.alb_zone_id
}

output "rds_payment_endpoint" {
  description = "RDS payment database endpoint"
  value       = module.rds.payment_endpoint
}

output "rds_ledger_endpoint" {
  description = "RDS ledger database endpoint"
  value       = module.rds.ledger_endpoint
}

output "rds_loan_endpoint" {
  description = "RDS loan database endpoint"
  value       = module.rds.loan_endpoint
}

output "msk_bootstrap_brokers" {
  description = "MSK bootstrap brokers"
  value       = module.msk.bootstrap_brokers
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK bootstrap brokers (TLS)"
  value       = module.msk.bootstrap_brokers_tls
}

output "ecr_payment_repo_url" {
  description = "ECR payment-service repository URL"
  value       = module.ecr.payment_repo_url
}

output "ecr_ledger_repo_url" {
  description = "ECR ledger-service repository URL"
  value       = module.ecr.ledger_repo_url
}

output "ecr_loan_repo_url" {
  description = "ECR loan-service repository URL"
  value       = module.ecr.loan_repo_url
}