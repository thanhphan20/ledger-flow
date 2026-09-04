# ECR Module - 3 Repositories

resource "aws_ecr_repository" "payment" {
  name                 = "${var.environment}/payment-service"
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = true
  }
  
  encryption_configuration {
    encryption_type = "AES256"
  }
  
  tags = {
    Name        = "${var.environment}-payment-repo"
    Environment = var.environment
    Service     = "payment"
  }
}

resource "aws_ecr_repository" "ledger" {
  name                 = "${var.environment}/ledger-service"
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = true
  }
  
  encryption_configuration {
    encryption_type = "AES256"
  }
  
  tags = {
    Name        = "${var.environment}-ledger-repo"
    Environment = var.environment
    Service     = "ledger"
  }
}

resource "aws_ecr_repository" "loan" {
  name                 = "${var.environment}/loan-service"
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = true
  }
  
  encryption_configuration {
    encryption_type = "AES256"
  }
  
  tags = {
    Name        = "${var.environment}-loan-repo"
    Environment = var.environment
    Service     = "loan"
  }
}

# ECR Repository Policy for GitHub Actions
resource "aws_ecr_repository_policy" "payment" {
  repository = aws_ecr_repository.payment.name
  policy     = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowGitHubActions"
      Effect = "Allow"
      Principal = {
        AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
      }
      Action = [
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ]
    }]
  }
}

resource "aws_ecr_repository_policy" "ledger" {
  repository = aws_ecr_repository.ledger.name
  policy     = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowGitHubActions"
      Effect = "Allow"
      Principal = {
        AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
      }
      Action = [
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ]
    }]
  }
}

resource "aws_ecr_repository_policy" "loan" {
  repository = aws_ecr_repository.loan.name
  policy     = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowGitHubActions"
      Effect = "Allow"
      Principal = {
        AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
      }
      Action = [
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ]
    }]
  }
}

data "aws_caller_identity" "current" {}