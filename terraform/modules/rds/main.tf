# RDS Module - 3 PostgreSQL instances (payment, ledger, loan)

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name        = "${var.environment}-db-subnet-group"
    Environment = var.environment
  }
}

# RDS Instances - Payment DB
resource "aws_db_instance" "payment" {
  identifier        = "${var.db_name_prefix}-payment"
  engine            = "postgres"
  engine_version    = "16.3"
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  storage_encrypted = true
  
  db_name  = "${var.db_name_prefix}_payment"
  username = var.db_username
  password = var.db_passwords.payment
  
  vpc_security_group_ids = [var.rds_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  
  instance_class         = var.rds_instance_class
  allocated_storage      = var.rds_allocated_storage
  storage_encrypted      = true
  multi_az               = var.rds_multi_az
  publicly_accessible    = false
  
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"
  deletion_protection     = false
  
  skip_final_snapshot = true
  
  tags = {
    Name        = "${var.environment}-payment-db"
    Environment = var.environment
    Service     = "payment"
  }
}

# RDS Instances - Ledger DB
resource "aws_db_instance" "ledger" {
  identifier        = "${var.db_name_prefix}-ledger"
  engine            = "postgres"
  engine_version    = "16.3"
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  storage_encrypted = true
  
  db_name  = "${var.db_name_prefix}_ledger"
  username = var.db_username
  password = var.db_passwords.ledger
  
  vpc_security_group_ids = [var.rds_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  
  instance_class         = var.rds_instance_class
  allocated_storage      = var.rds_allocated_storage
  storage_encrypted      = true
  multi_az               = var.rds_multi_az
  publicly_accessible    = false
  
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"
  deletion_protection     = false
  
  skip_final_snapshot = true
  
  tags = {
    Name        = "${var.environment}-ledger-db"
    Environment = var.environment
    Service     = "ledger"
  }
}

# RDS Instances - Loan DB
resource "aws_db_instance" "loan" {
  identifier        = "${var.db_name_prefix}-loan"
  engine            = "postgres"
  engine_version    = "16.3"
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  storage_encrypted = true
  
  db_name  = "${var.db_name_prefix}_loan"
  username = var.db_username
  password = var.db_passwords.loan
  
  vpc_security_group_ids = [var.rds_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  
  instance_class         = var.rds_instance_class
  allocated_storage      = var.rds_allocated_storage
  storage_encrypted      = true
  multi_az               = var.rds_multi_az
  publicly_accessible    = false
  
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"
  deletion_protection     = false
  
  skip_final_snapshot = true
  
  tags = {
    Name        = "${var.environment}-loan-db"
    Environment = var.environment
    Service     = "loan"
  }
}

# Parameter Group
resource "aws_db_parameter_group" "main" {
  name        = "${var.environment}-pg-params"
  family      = "postgres16"
  description = "Custom parameter group for LedgerFlow PostgreSQL"
  
  parameter {
    name  = "shared_buffers"
    value = "256MB"
  }
  
  parameter {
    name  = "max_connections"
    value = "100"
  }
  
  parameter {
    name  = "log_statement"
    value = "all"
  }
  
  tags = {
    Name        = "${var.environment}-pg-params"
    Environment = var.environment
  }
}