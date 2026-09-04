output "payment_endpoint" {
  description = "Payment database endpoint"
  value       = aws_db_instance.payment.endpoint
}

output "ledger_endpoint" {
  description = "Ledger database endpoint"
  value       = aws_db_instance.ledger.endpoint
}

output "loan_endpoint" {
  description = "Loan database endpoint"
  value       = aws_db_instance.loan.endpoint
}

output "payment_arn" {
  description = "Payment DB ARN"
  value       = aws_db_instance.payment.arn
}

output "ledger_arn" {
  description = "Ledger DB ARN"
  value       = aws_db_instance.ledger.arn
}

output "loan_arn" {
  description = "Loan DB ARN"
  value       = aws_db_instance.loan.arn
}