# Secrets Module - DB Passwords, JWT Secret

resource "aws_kms_key" "secrets" {
  description = "KMS key for Secrets Manager"
  deletion_window_in_days = 10
  
  tags = {
    Name        = "${var.environment}-secrets-kms"
    Environment = var.environment
  }
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${var.environment}-secrets"
  target_key_id = aws_kms_key.secrets.arn
}

# DB Secrets (one per service)
resource "aws_secretsmanager_secret" "payment_db" {
  name        = "${var.environment}/payment/db"
  description = "Payment database credentials"
  kms_key_id  = aws_kms_key.secrets.arn
  
  tags = {
    Name        = "${var.environment}-payment-db-secret"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "payment_db" {
  secret_id = aws_secretsmanager_secret.payment_db.id
  secret_string = jsonencode({
    username = var.db_username
    password = var.db_passwords.payment
  })
}

resource "aws_secretsmanager_secret" "ledger_db" {
  name        = "${var.environment}/ledger/db"
  description = "Ledger database credentials"
  kms_key_id  = aws_kms_key.secrets.arn
  
  tags = {
    Name        = "${var.environment}-ledger-db-secret"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "ledger_db" {
  secret_id = aws_secretsmanager_secret.ledger_db.id
  secret_string = jsonencode({
    username = var.db_username
    password = var.db_passwords.ledger
  })
}

resource "aws_secretsmanager_secret" "loan_db" {
  name        = "${var.environment}/loan/db"
  description = "Loan database credentials"
  kms_key_id  = aws_kms_key.secrets.arn
  
  tags = {
    Name        = "${var.environment}-loan-db-secret"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "loan_db" {
  secret_id = aws_secretsmanager_secret.loan_db.id
  secret_string = jsonencode({
    username = var.db_username
    password = var.db_passwords.loan
  })
}

# Combined DB Secrets (for easier injection into all services)
resource "aws_secretsmanager_secret" "db_all" {
  name        = "${var.environment}/db/all"
  description = "All database credentials combined"
  kms_key_id  = aws_kms_key.secrets.arn
  
  tags = {
    Name        = "${var.environment}-db-all-secret"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_all" {
  secret_id = aws_secretsmanager_secret.db_all.id
  secret_string = jsonencode({
    payment = {
      username = var.db_username
      password = var.db_passwords.payment
    }
    ledger = {
      username = var.db_username
      password = var.db_passwords.ledger
    }
    loan = {
      username = var.db_username
      password = var.db_passwords.loan
    }
  })
}

# JWT Secret
resource "aws_secretsmanager_secret" "jwt" {
  name        = "${var.environment}/jwt"
  description = "JWT signing secret"
  kms_key_id  = aws_kms_key.secrets.arn
  
  tags = {
    Name        = "${var.environment}-jwt-secret"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id = aws_secretsmanager_secret.jwt.id
  secret_string = jsonencode({
    jwt_secret = var.jwt_secret
  })
}