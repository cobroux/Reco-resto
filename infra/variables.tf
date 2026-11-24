variable "region" {
  description = "AWS region (to be provided, no default on purpose)"
  type        = string
}

variable "project" {
  description = "Name prefix; also the ECS cluster name (GitHub variable ECS_PROJECT)"
  type        = string
  default     = "reco-resto"
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the deploy role"
  type        = string
  default     = "emocio-tech/Reco-Resto"
}

variable "create_github_oidc_provider" {
  description = "Set to false if the account already has the token.actions.githubusercontent.com OIDC provider"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to every resource at creation (provider default_tags) and propagated to ECS tasks"
  type        = map(string)
  default     = { Owner = "kquach" }
}
