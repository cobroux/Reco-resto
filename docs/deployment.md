# Deployment and CI/CD

## How it works

- `.github/workflows/ci.yml` (pull requests): backend tests, frontend tests and build, then a Docker build of both images (no push).
- `.github/workflows/deploy.yml` (push to `main`): builds both images, pushes them to Amazon ECR (`<account>.dkr.ecr.<region>.amazonaws.com/reco-resto-<app>:<commit sha>`), registers a new ECS task definition revision and deploys it to AWS ECS Fargate (backend, then frontend).
- Architecture: one Application Load Balancer. `/api/*` goes to the backend (port 8080, health check `/api/hello`), everything else to the frontend (nginx, port 80). One origin, so no CORS.

## One-time setup

1. Apply the infrastructure (it creates the ECR repositories; no PAT or registry secret is needed):
   ```
   cd infra
   terraform init
   terraform apply -var region=<aws-region>
   ```
   Add `-var create_github_oidc_provider=false` if the AWS account already has the GitHub OIDC provider.
2. In GitHub (Settings > Secrets and variables > Actions > Variables), add:
   - `AWS_REGION`: the region used above;
   - `AWS_ROLE_ARN`: Terraform output `github_deploy_role_arn`;
   - `ECS_PROJECT`: `reco-resto` (the Terraform `project` variable; it is the cluster name and the service/task prefix).
3. First deploy: the ECS services start with the `:latest` image, which does not exist in ECR. They stay unhealthy until the first run of `deploy.yml` on `main` publishes and deploys a real image.

## Known limits

- HTTP only: add a domain and an ACM certificate to serve HTTPS.
- Default VPC, tasks with a public IP, one task per service: fine for a first deployment, not hardened for production.
- Terraform state is local; move it to a remote backend (S3) for team use.
