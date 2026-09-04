# LedgerFlow Terraform Infrastructure

Terraform modules for deploying LedgerFlow microservices to AWS using ECS Fargate, RDS PostgreSQL, and MSK Kafka.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS Region (us-east-1)                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                      VPC (10.0.0.0/16)                      ││
│  │  ┌──────────────┐  ┌──────────────┐                         ││
│  │  │ Public Subnet │  │ Public Subnet │  (AZ-a, AZ-b)        ││
│  │  │   (ALB)       │  │   (NAT GW)   │                        ││
│  │  └──────────────┘  └──────────────┘                         ││
│  │  ┌──────────────┐  ┌──────────────┐                         ││
│  │  │ Private Subnet│  │ Private Subnet│  (AZ-a, AZ-b)        ││
│  │  │  (ECS Tasks) │  │  (RDS, MSK)  │                        ││
│  │  └──────────────┘  └──────────────┘                         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Components

| Component | Service | Details |
|-----------|---------|---------|
| Network | VPC | 2 AZs, public/private subnets, NAT gateways |
| Compute | ECS Fargate | 3 services, ALB ingress, Fargate tasks |
| Database | RDS PostgreSQL | 3 instances (payment, ledger, loan) |
| Messaging | Amazon MSK | 2 topics, IAM auth |
| Registry | ECR | 3 repositories |
| Secrets | Secrets Manager | DB passwords, JWT secret |
| Ingress | ALB | HTTPS, path-based routing |

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **S3 Bucket** for Terraform state: `ledgerflow-terraform-state`
3. **DynamoDB Table** for state locking: `terraform-lock`
4. **ACM Certificate** for ALB HTTPS (optional)
5. **GitHub Secrets** (see below)

## Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `AWS_ROLE_ARN` | IAM role ARN for GitHub Actions to assume |
| `DB_PASSWORD_PAYMENT` | Payment DB password |
| `DB_PASSWORD_LEDGER` | Ledger DB password |
| `DB_PASSWORD_LOAN` | Loan DB password |
| `JWT_SECRET` | Base64-encoded JWT secret (32+ bytes) |
| `TF_API_TOKEN` | Terraform Cloud API token (optional) |

## Deployment

### Prerequisites (run once)

```bash
# Create S3 bucket for state
aws s3 mb s3://ledgerflow-terraform-state --region us-east-1
aws s3api put-bucket-versioning --bucket ledgerflow-terraform-state --versioning-configuration Status=Enabled

# Create DynamoDB lock table
aws dynamodb create-table \
  --table-name terraform-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### Deploy via GitHub Actions

1. Push to `main` branch → triggers `plan`
2. Manual workflow dispatch with `action: apply` → deploys
3. Or manual workflow dispatch with `action: destroy` → tears down

### Manual Deploy

```bash
cd terraform/prod

# Initialize
terraform init

# Plan
terraform plan \
  -var="aws_region=us-east-1" \
  -var="environment=prod" \
  -var="db_passwords={\"payment\":\"...\",\"ledger\":\"...\",\"loan\":\"...\"}" \
  -var="jwt_secret=..." \
  -var="image_tag=latest"

# Apply
terraform apply \
  -var="aws_region=us-east-1" \
  -var="environment=prod" \
  -var="db_passwords={\"payment\":\"...\",\"ledger\":\"...\",\"loan\":\"...\"}" \
  -var="jwt_secret=..." \
  -var="image_tag=latest"
```

## Modules

| Module | Description |
|--------|-------------|
| `vpc` | VPC, subnets, NAT, security groups |
| `rds` | 3 PostgreSQL instances (payment, ledger, loan) |
| `msk` | MSK Kafka cluster + topics |
| `ecr` | 3 ECR repositories |
| `secrets` | DB passwords, JWT secret in Secrets Manager |
| `ecs` | ECS cluster, ALB, services, task definitions |
| `prod` | Root module composing all modules |

## Post-Deploy

1. **Build & Push Images** (CI/CD handles this):
   ```bash
   # Manual build
   cd payment-service
   docker build -t <account>.dkr.ecr.us-east-1.amazonaws.com/prod/payment-service:latest .
   docker push <account>.dkr.ecr.us-east-1.amazonaws.com/prod/payment-service:latest
   ```

2. **Verify Deployment**:
   - Check ALB DNS in Terraform outputs
   - Test endpoints: `https://<alb-dns>/actuator/health`
   - Check CloudWatch logs: `/ecs/prod`

## Cleanup

```bash
# Via GitHub Actions (workflow_dispatch with action: destroy)
# Or manually:
cd terraform/prod
terraform destroy -var="..." 
```

## Cost Optimization (Single Prod)

- RDS: `db.t3.micro`, single-AZ, 20GB
- MSK: `kafka.t3.small`, 2 brokers
- ECS Fargate: 0.5 vCPU, 1GB, 2 tasks per service
- NAT Gateway: 2 (one per AZ)

Estimated monthly cost: ~$150-200 (single prod, minimal traffic)

## Security

- All traffic in private subnets
- ALB only ingress on 443/80
- RDS/MSK only accessible from ECS tasks SG
- Secrets encrypted with KMS
- ALB HTTPS with ACM cert
- IAM roles for ECS tasks (least privilege)

## Customization

Key variables in `terraform/variables.tf`:

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `us-east-1` | AWS region |
| `vpc_cidr` | `10.0.0.0/16` | VPC CIDR |
| `rds_instance_class` | `db.t3.micro` | RDS instance type |
| `msk_instance_type` | `kafka.t3.small` | MSK broker type |
| `ecs_task_cpu` | `512` | CPU units per task |
| `ecs_task_memory` | `1024` | Memory MiB per task |
| `desired_count` | `2` | Tasks per service |