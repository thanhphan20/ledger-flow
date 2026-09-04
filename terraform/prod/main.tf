# Production Environment - Root Module

module "vpc" {
  source = "../modules/vpc"
  
  vpc_cidr            = var.vpc_cidr
  public_subnet_cidrs = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  environment         = var.environment
  container_port      = var.container_port
}

module "secrets" {
  source = "../modules/secrets"
  
  environment  = var.environment
  db_username  = var.db_username
  db_passwords = var.db_passwords
  jwt_secret   = var.jwt_secret
}

module "rds" {
  source = "../modules/rds"
  
  environment             = var.environment
  private_subnet_ids      = module.vpc.private_subnet_ids
  rds_security_group_id   = module.vpc.rds_security_group_id
  db_name_prefix          = var.db_name_prefix
  db_username             = var.db_username
  db_passwords            = var.db_passwords
  rds_instance_class      = var.rds_instance_class
  rds_allocated_storage   = var.rds_allocated_storage
  rds_multi_az            = var.rds_multi_az
}

module "msk" {
  source = "../modules/msk"
  
  environment             = var.environment
  private_subnet_ids      = module.vpc.private_subnet_ids
  msk_security_group_id   = module.vpc.msk_security_group_id
  msk_instance_type       = var.msk_instance_type
  msk_number_of_brokers   = var.msk_number_of_brokers
}

module "ecr" {
  source = "../modules/ecr"
  
  environment = var.environment
}

module "secrets" {
  source = "../modules/secrets"
  
  environment  = var.environment
  db_username  = var.db_username
  db_passwords = var.db_passwords
  jwt_secret   = var.jwt_secret
}

module "ecs" {
  source = "../modules/ecs"
  
  environment                = var.environment
  vpc_id                     = module.vpc.vpc_id
  public_subnet_ids          = module.vpc.public_subnet_ids
  private_subnet_ids         = module.vpc.private_subnet_ids
  alb_security_group_id      = module.vpc.alb_security_group_id
  ecs_tasks_security_group_id = module.vpc.ecs_tasks_security_group_id
  container_port             = var.container_port
  ecs_task_cpu               = var.ecs_task_cpu
  ecs_task_memory            = var.ecs_task_memory
  desired_count              = var.desired_count
  container_port             = var.container_port
  aws_region                 = var.aws_region
  environment                = var.environment
  image_tag                  = var.image_tag
  certificate_arn            = var.certificate_arn
  private_subnet_ids         = module.vpc.private_subnet_ids
  public_subnet_ids          = module.vpc.public_subnet_ids
  vpc_id                     = module.vpc.vpc_id
  alb_security_group_id      = module.vpc.alb_security_group_id
  ecs_tasks_security_group_id = module.vpc.ecs_tasks_security_group_id
  container_port             = var.container_port
  secrets_arn                = module.secrets.db_all_secret_arn
  db_secrets_arn             = module.secrets.db_all_secret_arn
  jwt_secret_arn             = module.secrets.jwt_secret_arn
  db_name_prefix             = var.db_name_prefix
  db_secrets_arn             = module.secrets.db_all_secret_arn
  jwt_secret_arn             = module.secrets.jwt_secret_arn
  private_subnet_ids         = module.vpc.private_subnet_ids
  public_subnet_ids          = module.vpc.public_subnet_ids
  vpc_id                     = module.vpc.vpc_id
  alb_security_group_id      = module.vpc.alb_security_group_id
  ecs_tasks_security_group_id = module.vpc.ecs_tasks_security_group_id
  container_port             = var.container_port
  ecs_task_cpu               = var.ecs_task_cpu
  ecs_task_memory            = var.ecs_task_memory
  desired_count              = var.desired_count
  certificate_arn            = var.certificate_arn
  db_name_prefix             = var.db_name_prefix
  db_secrets_arn             = module.secrets.db_all_secret_arn
  jwt_secret_arn             = module.secrets.jwt_secret_arn