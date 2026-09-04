output "cluster_id" {
  description = "ECS cluster ID"
  value       = aws_ecs_cluster.main.id
}

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = aws_lb.main.dns_name
}

output "alb_zone_id" {
  description = "ALB zone ID"
  value       = aws_lb.main.zone_id
}

output "payment_target_group_arn" {
  description = "Payment target group ARN"
  value       = aws_lb_target_group.payment.arn
}

output "loan_target_group_arn" {
  description = "Loan target group ARN"
  value       = aws_lb_target_group.loan.arn
}