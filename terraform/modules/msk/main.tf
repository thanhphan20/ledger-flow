# MSK Module - Kafka Cluster + Topics

# MSK Cluster
resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.environment}-kafka"
  kafka_version          = "3.6"
  number_of_broker_nodes = var.msk_number_of_brokers
  
  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = var.private_subnet_ids
    security_groups = [var.msk_security_group_id]
    
    storage_info {
      ebs_storage_info {
        volume_size = 10
      }
    }
  }
  
  encryption_info {
    encryption_at_rest_kms_key_arn = aws_kms_key.msk.arn
  }
  
  client_authentication {
    sasl {
      iam = true
    }
  }
  
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }
  
  tags = {
    Name        = "${var.environment}-kafka"
    Environment = var.environment
  }
}

# KMS Key for MSK encryption
resource "aws_kms_key" "msk" {
  description = "KMS key for MSK encryption"
  deletion_window_in_days = 10
  
  tags = {
    Name        = "${var.environment}-msk-kms"
    Environment = var.environment
  }
}

# CloudWatch Log Group for MSK
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${var.environment}"
  retention_in_days = 30
  
  tags = {
    Name        = "${var.environment}-msk-logs"
    Environment = var.environment
  }
}

# Kafka Topics
resource "aws_msk_topic" "payment_completed" {
  cluster_arn  = aws_msk_cluster.main.arn
  name         = "payment.completed"
  partitions   = 3
  replication_factor = 2
}

resource "aws_msk_topic" "loan_approved" {
  cluster_arn  = aws_msk_cluster.main.arn
  name         = "loan.approved"
  partitions   = 3
  replication_factor = 2
}