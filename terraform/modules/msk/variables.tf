variable "environment" {
  description = "Environment name"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "msk_security_group_id" {
  description = "MSK security group ID"
  type        = string
}

variable "msk_instance_type" {
  description = "MSK broker instance type"
  type        = string
}

variable "msk_number_of_brokers" {
  description = "Number of MSK brokers"
  type        = number
}