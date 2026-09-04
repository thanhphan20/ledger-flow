variable "environment" {
  description = "Environment name"
  type        = string
}

variable "db_username" {
  description = "Database username"
  type        = string
}

variable "db_passwords" {
  description = "Map of database passwords"
  type        = map(string)
}

variable "jwt_secret" {
  description = "JWT secret for services (base64 encoded, min 32 bytes)"
  type        = string
  sensitive   = true
}