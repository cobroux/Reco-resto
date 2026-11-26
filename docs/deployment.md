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
2. **deploy**: copies `docker-compose.yml` to the instance over SSH, runs `docker compose pull` and `up -d` with the commit-SHA tag, then checks `http://<host>/api/hello`.

The frontend container (nginx) is the only one published (port 80) and proxies `/api/` to the backend container (`frontend/nginx.conf`). Files: `docker-compose.yml`, `frontend/nginx.conf`.

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
   - variable `EC2_USER`: `ec2-user`;
   - secret `LLM_API_KEY`, variables `LLM_BASE_URL` and `LLM_MODEL`: written to `~/.env` on the instance at each deploy (mode 600) and loaded by the backend container through `env_file`.
   - secret `GOOGLE_PLACES_API_KEY` (Google Places API (New) key, spec 008): written to `~/.env` the same way at each deploy; never committed with a value.
   - secret `UNSPLASH_ACCESS_KEY` (Unsplash application "Access Key", spec 007 amendment (b)): optional; blank means cards get no Unsplash cuisine photo. Never committed with a value, never sent to the browser nor logged. **Not yet written by `deploy.yml`**: add it to the GitHub secrets and to the `~/.env` lines of `.github/workflows/deploy.yml` (spec 007 plan OQ-10). Other settings `CARD_ENRICHMENT_UNSPLASH_BASE_URL`, `CARD_ENRICHMENT_UNSPLASH_APP_NAME` (`reco_resto`), `CARD_ENRICHMENT_UNSPLASH_PER_PAGE` (15), `CARD_ENRICHMENT_UNSPLASH_CACHE_TTL` (`1h`) have defaults in `application.yml`.
4. **Images are public**: after the first run of `deploy.yml`, open each package (`reco-resto-backend`, `reco-resto-frontend`) in the GitHub organisation's Packages and set its visibility to public. The instance pulls them without authentication.

## Known limits

- HTTP only: add a domain and a certificate to serve HTTPS.
- A single small instance, with no redundancy; its public IP changes on stop/start unless an Elastic IP is attached.
- The SSH host key is read with `ssh-keyscan` at each run (trust on first use), and port 22 is open to the Internet.
- The instance is managed by hand: its configuration is not versioned in the repository.
- Card enrichment (spec 007) makes outbound requests: HTTP/HTTPS to restaurant websites (ports 80/443) and, with a key, HTTPS to `api.unsplash.com`; the user's browser loads Unsplash photos directly from `images.unsplash.com`. The Unsplash demo limit is 50 requests per hour (answers are reused for 1 hour per cuisine, in memory); above it, cards keep the tablecloth.
