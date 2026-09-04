variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs"
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "ALB security group ID"
  type        = string
}

variable "ecs_tasks_security_group_id" {
  description = "ECS tasks security group ID"
  type        = string
}

variable "container_port" {
  description = "Container port for services"
  type        = number
}

variable "ecs_task_cpu" {
  description = "CPU units for ECS tasks"
  type        = number
}

variable "ecs_task_memory" {
  description = "Memory (MiB) for ECS tasks"
  type        = number
}

variable "desired_count" {
  description = "Desired number of tasks per service"
  type        = number
}

variable "container_port" {
  description = "Container port for services"
  type        = number
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

variable "certificate_arn" {
  description = "ACM certificate ARN for ALB HTTPS"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "Public subnet IDs"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "alb_security_group_id" {
  description = "ALB security group ID"
  type        = string
}

variable "ecs_tasks_security_group_id" {
  description = "ECS tasks security group ID"
  type        = string
}

variable "container_port" {
  description = "Container port for services"
  type        = number
}

variable "secrets_arn" {
  description = "Secrets Manager ARN for DB secrets"
  type        = string
}

variable "db_secrets_arn" {
  description = "Secrets Manager ARN for DB secrets"
  type        = string
}

variable "jwt_secret_arn" {
  description = "Secrets Manager ARN for JWT secret"
  type        = string
}

variable "db_secrets_arn" {
  description = "Secrets Manager ARN for DB secrets"
  type        = string
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

variable "container_port" {
  description = "Container port for services"
  type        = number
}

variable "ecs_task_cpu" {
  description = "CPU units for ECS tasks"
  type        = number
}

variable "ecs_task_memory" {
  description = "Memory (MiB) for ECS tasks"
  type        = number
}

variable "desired_count" {
  description = "Desired number of tasks per service"
  type        = number
}

variable "certificate_arn" {
  description = "ACM certificate ARN for ALB HTTPS"
  type        = string
}

variable "db_name_prefix" {
  description = "Prefix for database names"
  type        = string
}

variable "db_secrets_arn" {
  description = "Secrets Manager ARN for DB secrets"
  type        = string
}

variable "jwt_secret_arn" {
  description = "Secrets Manager ARN for JWT secret"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "Public subnet IDs"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "alb_security_group_id" {
  description = "ALB security group ID"
  type        = string
}

variable "ecs_tasks_security_group_id" {
  description = "ECS tasks security group ID"
  type        = string
}

variable "container_port" {
  description = "Container port for services"
  type        = number
}

variable "ecs_task_cpu" {
  description = "CPU units for ECS tasks"
  type        = number
}

variable "ecs_task_memory" {
  description = "Memory (MiB) for ECS tasks"
  type        = number
}

variable "desired_count" {
  description = "Desired number of tasks per service"
  type        = number
}

variable "certificate_arn" {
  description = "ACM certificate ARN for ALB HTTPS"
  type        = string
}