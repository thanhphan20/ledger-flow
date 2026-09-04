output "payment_db_secret_arn" {
  description = "Payment DB secret ARN"
  value       = aws_secretsmanager_secret.payment_db.arn
}

output "ledger_db_secret_arn" {
  description = "Ledger DB secret ARN"
  value       = aws_secretsmanager_secret.ledger_db.arn
}

output "loan_db_secret_arn" {
  description = "Loan DB secret ARN"
  value       = aws_secretsmanager_secret.loan_db.arn
}

output "db_all_secret_arn" {
  description = "Combined DB secrets ARN"
  value       = aws_secretsmanager_secret.db_all.arn
}

output "jwt_secret_arn" {
  description = "JWT secret ARN"
  value       = aws_secretsmanager_secret.jwt.arn
}

output "kms_key_arn" {
  description = "KMS key ARN for secrets"
  value       = aws_kms_key.secrets.arn
}