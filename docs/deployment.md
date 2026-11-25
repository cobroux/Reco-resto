# Deployment and CI/CD

## Target

One EC2 instance in `eu-west-3`:

- Name `reco-restau` (`i-0fbb24dfafd44ad84`), type `t4g.micro` (1 GiB RAM), Amazon Linux 2023, **ARM64 (Graviton)**.
- Public ports open in its security group: 80, 443 and 22. The backend (port 8080) is not exposed: it must stay reachable only from the frontend (nginx, port 80), which routes `/api/*` to it. One origin, so no CORS.
- No SSH key pair is attached. The instance profile is `kquach-ec2`.

Images must be built for `linux/arm64` to run on this instance.

## CI

`.github/workflows/ci.yml` (pull requests): backend tests, frontend tests and build, then a Docker build of both images (no push).

## CD

`.github/workflows/deploy.yml` (push to `main`):

1. **build**: builds both images for `linux/arm64` and pushes them to GitHub Container Registry (`ghcr.io/<owner>/reco-resto-<app>:<commit sha>` and `:latest`).
2. **deploy**: copies `docker-compose.yml` and `Caddyfile` to the instance over SSH, runs `docker compose pull` and `up -d` with the commit-SHA tag, then checks `https://<domain>/api/hello`.

Only the Caddy container is published (ports 80 and 443). It serves HTTPS with a Let's Encrypt certificate that it obtains and renews by itself, and forwards to the frontend container (nginx), which proxies `/api/` to the backend container (`frontend/nginx.conf`). Files: `docker-compose.yml`, `Caddyfile`, `frontend/nginx.conf`.

The domain name is set in `Caddyfile` only (currently the public DNS name of the instance). It must resolve to the instance, and ports 80 and 443 must be open to the Internet. If the instance public IP changes (stop/start without an Elastic IP), the name changes too: update `Caddyfile`, or attach an Elastic IP.

### One-time setup

1. **Docker on the instance** (Amazon Linux 2023, ARM64), from an EC2 Instance Connect session:
   ```
   sudo dnf install -y docker
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64      -o /usr/local/lib/docker/cli-plugins/docker-compose
   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```
2. **SSH key for the deploy**: generate a dedicated key pair (`ssh-keygen -t ed25519 -f deploy_key -N ""`), append `deploy_key.pub` to `~/.ssh/authorized_keys` on the instance, then delete the local files after step 3.
3. **GitHub** (Settings > Secrets and variables > Actions):
   - secret `EC2_SSH_KEY`: content of the private key `deploy_key`;
   - secret `EC2_HOST`: public IP or DNS name of the instance;
   - variable `EC2_USER`: `ec2-user`.
4. **Images are public**: after the first run of `deploy.yml`, open each package (`reco-resto-backend`, `reco-resto-frontend`) in the GitHub organisation's Packages and set its visibility to public. The instance pulls them without authentication.

## Known limits

- Let's Encrypt limits failed validations (about 5 per hour per name): do not redeploy in a loop if the certificate is not issued. Logs: `docker logs <caddy container>`.
- A single small instance, with no redundancy; its public IP changes on stop/start unless an Elastic IP is attached.
- The SSH host key is read with `ssh-keyscan` at each run (trust on first use), and port 22 is open to the Internet.
- The instance is managed by hand: its configuration is not versioned in the repository.
