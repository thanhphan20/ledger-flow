# ECS Module - Cluster, ALB, Services, Task Definitions

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${var.environment}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  
  tags = {
    Name        = "${var.environment}-cluster"
    Environment = var.environment
  }
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.environment}"
  retention_in_days = 30
  
  tags = {
    Name        = "${var.environment}-ecs-logs"
    Environment = var.environment
  }
}

# ALB
resource "aws_lb" "main" {
  name               = "${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets             = var.public_subnet_ids
  enable_deletion_protection = false
  
  access_logs {
    bucket  = aws_s3_bucket.alb_logs.bucket
    prefix  = "alb-logs"
    enabled = true
  }
  
  tags = {
    Name        = "${var.environment}-alb"
    Environment = var.environment
  }
}

# S3 Bucket for ALB Access Logs
resource "aws_s3_bucket" "alb_logs" {
  bucket = "${var.environment}-ledgerflow-alb-logs-${random_id.suffix.hex}"
  
  lifecycle_rule {
    enabled = true
    expiration {
      days = 30
    }
  }
}

resource "random_id" "suffix" {
  byte_length = 8
}

# ALB Target Groups
resource "aws_lb_target_group" "payment" {
  name        = "${var.environment}-payment-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  
  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200-399"
  }
  
  tags = {
    Name        = "${var.environment}-payment-tg"
    Environment = var.environment
  }
}

resource "aws_lb_target_group" "loan" {
  name        = "${var.environment}-loan-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  
  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200-399"
  }
  
  tags = {
    Name        = "${var.environment}-loan-tg"
    Environment = var.environment
  }
}

# ALB Listener (HTTP -> HTTPS redirect)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# ALB Listener (HTTPS)
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\":\"Not found\"}"
      status_code  = "404"
    }
  }
}

# ALB Listener Rules - Payment
resource "aws_lb_listener_rule" "payment" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.payment.arn
  }
  
  condition {
    path_pattern {
      values = ["/api/v1/payments*"]
    }
  }
}

# ALB Listener Rules - Loan
resource "aws_lb_listener_rule" "loan" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 110
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.loan.arn
  }
  
  condition {
    path_pattern {
      values = ["/api/v1/loans*"]
    }
  }
}

# ALB Listener Rules - Health (payment)
resource "aws_lb_listener_rule" "payment_health" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 101
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.payment.arn
  }
  
  condition {
    path_pattern {
      values = ["/actuator/health*"]
    }
  }
}

# ALB Listener Rules - Health (loan)
resource "aws_lb_listener_rule" "loan_health" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 111
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.loan.arn
  }
  
  condition {
    path_pattern {
      values = ["/actuator/health*"]
    }
  }
}

# IAM Roles
resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.environment}-ecs-task-execution-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_task_secrets" {
  name = "${var.environment}-ecs-tasks-secrets"
  role = aws_iam_role.ecs_task_execution.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "kms:Decrypt"
      ]
      Resource = [
        var.secrets_arn,
        var.db_secrets_arn
      ]
    }]
  }
}

resource "aws_iam_role" "ecs_task" {
  name = "${var.environment}-ecs-task-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  }
}

resource "aws_iam_role_policy" "ecs_task_kafka" {
  name = "${var.environment}-ecs-task-kafka"
  role = aws_iam_role.ecs_task.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kafka-cluster:Connect",
        "kafka-cluster:DescribeCluster",
        "kafka-cluster:DescribeClusterDynamicConfiguration",
        "kafka-cluster:AlterCluster",
        "kafka-cluster:AlterClusterDynamicConfiguration",
        "kafka:DescribeCluster",
        "kafka:DescribeClusterV2"
      ]
      Resource = [aws_msk_cluster.main.arn]
    }]
  }
}

# Task Definitions

# Payment Service Task Definition
resource "aws_ecs_task_definition" "payment" {
  family                   = "${var.environment}-payment"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  container_definitions = jsonencode([{
    name      = "payment-service"
    image     = "${aws_ecr_repository.payment.repository_url}:${var.image_tag}"
    cpu       = var.ecs_task_cpu
    memory    = var.ecs_task_memory
    essential = true
    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${module.rds.payment_endpoint}/${var.db_name_prefix}_payment" },
      { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "validate" }
    ]
    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.db_secrets_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.db_secrets_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = "${var.jwt_secret_arn}:jwt_secret::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "payment"
      }
    }
  }]
  
  tags = {
    Name        = "${var.environment}-payment"
    Environment = var.environment
  }
}

# Ledger Service Task Definition
resource "aws_ecs_task_definition" "ledger" {
  family                   = "${var.environment}-ledger"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  container_definitions = jsonencode([{
    name      = "ledger-service"
    image     = "${aws_ecr_repository.ledger.repository_url}:${var.image_tag}"
    cpu       = var.ecs_task_cpu
    memory    = var.ecs_task_memory
    essential = true
    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${module.rds.ledger_endpoint}/${var.db_name_prefix}_ledger" },
      { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "validate" }
    ]
    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.db_secrets_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.db_secrets_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = "${var.jwt_secret_arn}:jwt_secret::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ledger"
      }
    }
  }]
  
  tags = {
    Name        = "${var.environment}-ledger"
    Environment = var.environment
  }
}

# Loan Service Task Definition
resource "aws_ecs_task_definition" "loan" {
  family                   = "${var.environment}-loan"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  container_definitions = jsonencode([{
    name      = "loan-service"
    image     = "${aws_ecr_repository.loan.repository_url}:${var.image_tag}"
    cpu       = var.ecs_task_cpu
    memory    = var.ecs_task_memory
    essential = true
    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${module.rds.loan_endpoint}/${var.db_name_prefix}_loan" },
      { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "validate" }
    ]
    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.db_secrets_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.db_secrets_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = "${var.jwt_secret_arn}:jwt_secret::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "loan"
      }
    }
  }]
  
  tags = {
    Name        = "${var.environment}-loan"
    Environment = var.environment
  }
}

# ECS Services

resource "aws_ecs_service" "payment" {
  name            = "${var.environment}-payment"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.payment.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  
  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }
  
  load_balancer {
    target_group_arn = aws_lb_target_group.payment.arn
    container_name   = "payment-service"
    container_port   = var.container_port
  }
  
  deployment_controller {
    type = "ECS"
  }
  
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  
  tags = {
    Name        = "${var.environment}-payment"
    Environment = var.environment
  }
}

resource "aws_ecs_service" "ledger" {
  name            = "${var.environment}-ledger"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ledger.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  
  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }
  
  tags = {
    Name        = "${var.environment}-ledger"
    Environment = var.environment
  }
}

resource "aws_ecs_service" "loan" {
  name            = "${var.environment}-loan"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.loan.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  
  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }
  
  load_balancer {
    target_group_arn = aws_lb_target_group.loan.arn
    container_name   = "loan-service"
    container_port   = var.container_port
  }
  
  deployment_controller {
    type = "ECS"
  }
  
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  
  tags = {
    Name        = "${var.environment}-loan"
    Environment = var.environment
  }
}