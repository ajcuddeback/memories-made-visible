# Production deployment

Step-by-step from "nothing" to a live site. Two hosting shapes are covered; pick one.

- **A. One small VPS with Docker Compose** — cheapest, fully under your control, ~$6–12/mo.
- **B. Managed platform (Railway)** — no server to maintain, ~$10–20/mo.

Both use the same images, the same env vars, real S3 and your Namecheap mailbox. Do the **shared setup** first.

---

> `CONTACT_EMAIL`, `CONTACT_PHONE`, `CONTACT_INSTAGRAM` are the direct contact details shown on the booking page, about page and footer for people who'd rather not use the request form. Phone and Instagram are optional and hidden when blank.
>
> `APP_TIME_ZONE` (default `America/New_York`) is the studio's zone: "today", the two-hour booking notice and upcoming pop-ups are judged in it, so the container being in UTC doesn't matter.

## Shared setup (do once)

### 1. Domain & DNS (Namecheap)

You'll point the domain at the host in the last step. For now, note that the site assumes
`https://www.memoriesmadevisible.com`. If the canonical domain is different, change it in three places and rebuild:
`frontend/src/app/core/site.ts`, `frontend/public/robots.txt`, `frontend/public/sitemap.xml`.

### 2. S3 bucket for photos (AWS)

1. AWS Console → S3 → **Create bucket**: name e.g. `mmv-photos`, region `us-east-1`, keep *Block all public access* **on**.
2. IAM → **Users** → create `mmv-backend` with *programmatic access*; attach this inline policy (replace the bucket name):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
       "Resource": "arn:aws:s3:::mmv-photos/*"
     }]
   }
   ```
   Save the access key ID and secret — they become `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`.
3. Make photos readable by visitors. Either:
   - **CloudFront (recommended)**: create a distribution with the bucket as origin (Origin Access Control), then set
     `S3_PUBLIC_BASE_URL=https://<your-distribution>.cloudfront.net`. Fast, cached, HTTPS, no public bucket.
   - **No CDN**: leave `S3_PUBLIC_BASE_URL` blank. The API hands out 12-hour presigned URLs. Works, slightly slower.

### 3. Email (Namecheap Private Email)

- Mailboxes/aliases: `hello@` is the real mailbox; `admin@` and `bookings@` are aliases of it.
- If 2FA is on for `hello@`, create an **application password** in Private Email's security settings and use that.
- DNS: confirm SPF (`v=spf1 include:spf.privateemail.com ~all`) and DKIM records exist in Namecheap Advanced DNS,
  otherwise booking emails land in spam.

Values: `SMTP_HOST=mail.privateemail.com`, `SMTP_PORT=587`, `SMTP_USERNAME=hello@memoriesmadevisible.com`,
`SMTP_PASSWORD=<mailbox or app password>`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`.

### 4. Secrets

```bash
openssl rand -base64 48     # → APP_JWT_SECRET (≥ 32 chars)
openssl rand -base64 24     # → POSTGRES_PASSWORD (option A only)
```
Pick strong admin passwords → `ADMIN_ERICA_PASSWORD`, `ADMIN_AUSTIN_PASSWORD`. Changing one later and restarting the
backend is the password-reset procedure.

### 5. Container images

The GitHub Actions workflow builds and pushes both images on every push to `main`:

- `ghcr.io/ajcuddeback/memories-made-visible-backend:main`
- `ghcr.io/ajcuddeback/memories-made-visible-frontend:main`

They are **private by default**. Either make them public (GitHub → Packages → package settings → *Change visibility*)
or have the host log in to GHCR with a GitHub personal access token (`read:packages`). Tagging a release (`git tag v1.0.0
&& git push --tags`) also publishes `:1.0.0` tags, which is what you should deploy — not a moving `:main`.

---

## Option A — VPS + Docker Compose

Works on any Ubuntu box (DigitalOcean/Hetzner/Lightsail, 1 vCPU / 2 GB is plenty).

1. **Provision** an Ubuntu 24.04 server; point DNS `A` records for `memoriesmadevisible.com` and `www` at its IP.
2. **Install Docker**: `curl -fsSL https://get.docker.com | sh` and `sudo usermod -aG docker $USER` (re-login).
3. **Get the compose file**:
   ```bash
   git clone https://github.com/ajcuddeback/memories-made-visible.git && cd memories-made-visible
   cp .env.example .env && nano .env
   ```
   Fill in everything from the shared setup, plus:
   ```
   POSTGRES_PASSWORD=<generated>
   APP_SITE_URL=https://www.memoriesmadevisible.com
   APP_CORS_ORIGINS=https://www.memoriesmadevisible.com
   BACKEND_IMAGE=ghcr.io/ajcuddeback/memories-made-visible-backend:1.0.0
   FRONTEND_IMAGE=ghcr.io/ajcuddeback/memories-made-visible-frontend:1.0.0
   HTTP_PORT=8080
   ```
   (`HTTP_PORT=8080` because Caddy will own 80/443 in the next step. Leave `DATABASE_*` unset — the compose file
   wires the bundled Postgres.)
4. **Start the stack**:
   ```bash
   docker login ghcr.io          # only if the packages are private
   docker compose -f docker-compose.prod.yml pull
   docker compose -f docker-compose.prod.yml up -d
   docker compose -f docker-compose.prod.yml logs -f backend   # wait for "Started MmvApplication"
   curl -s localhost:8080/healthz && curl -s localhost:8080/api/pricing | head -c 100
   ```
5. **TLS with Caddy** (automatic Let's Encrypt):
   ```bash
   sudo apt install -y caddy
   sudo tee /etc/caddy/Caddyfile >/dev/null <<'CADDY'
   memoriesmadevisible.com {
       redir https://www.memoriesmadevisible.com{uri} permanent
   }
   www.memoriesmadevisible.com {
       encode zstd gzip
       reverse_proxy localhost:8080
   }
   CADDY
   sudo systemctl reload caddy
   ```
   Caddy fetches certificates on first request. Open https://www.memoriesmadevisible.com.
6. **Backups**: nightly `pg_dump` to S3 is the minimum —
   ```bash
   docker compose -f docker-compose.prod.yml exec -T db pg_dump -U mmv mmv | gzip > backup-$(date +%F).sql.gz
   ```
   Put that in cron and copy the file off the box (e.g. `aws s3 cp … s3://mmv-backups/`). Alternatively use a managed
   Postgres (Neon/Railway/DO) and set `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` in `.env`, dropping the
   `db` service from the compose file.
7. **Updating**: bump the two image tags in `.env`, then
   `docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`.
   Flyway applies any new migrations on start. Rollback = set the previous tag and repeat.

## Option B — Railway (managed)

1. Create a Railway project. Add **PostgreSQL** (Railway's) — or use Neon and skip this.
2. **Backend service** → *Deploy from Docker image* → `ghcr.io/ajcuddeback/memories-made-visible-backend:1.0.0`
   (add GHCR credentials under the service's registry settings if the package is private). Variables: everything
   from the shared setup, `DATABASE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require`, `DATABASE_USERNAME`,
   `DATABASE_PASSWORD` (from the Postgres service), `PORT=8080`, `APP_SITE_URL`, `APP_CORS_ORIGINS`. Enable *private
   networking*; note its internal hostname (e.g. `backend.railway.internal`).
3. **Frontend service** → Docker image `…-frontend:1.0.0`. Variables: `BACKEND_URL=http://backend.railway.internal:8080`.
   Generate a public domain, then add the custom domain `www.memoriesmadevisible.com` and create the CNAME Railway
   shows you in Namecheap. Railway handles TLS.
4. Health checks: backend `/actuator/health`, frontend `/healthz`.
5. Updating: change the image tag on each service (or point both at `:main` for auto-deploys from CI).

---

## Go-live checklist

- [ ] `https://www.memoriesmadevisible.com/` loads; `/pricing` shows prices (API reachable).
- [ ] `/studio` → sign in with the production admin passwords.
- [ ] Submit a real booking from `/book` → email arrives at `bookings@` (check spam the first time).
- [ ] Upload a photo in admin → appears on the site, URL is `S3_PUBLIC_BASE_URL/...` or a presigned link.
- [ ] `robots.txt` and `sitemap.xml` resolve; submit the sitemap in Google Search Console.
- [ ] Create a Google Business Profile (Winter Haven, FL) and add the website URL — for local search this matters as
      much as anything on the site.
- [ ] Backups scheduled (option A) or managed-DB backups enabled (option B).
- [ ] Turn on `NOTIFY_CUSTOMER=true` once SPF/DKIM are confirmed, if you want customers to get acknowledgements.

## Environment variable reference

See `.env.example` — every variable is documented inline. The ones that must be set for production:
`ADMIN_*_PASSWORD`, `APP_JWT_SECRET`, `APP_SITE_URL`, `APP_CORS_ORIGINS`, `SMTP_*`, `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `S3_BUCKET`, `AWS_REGION`, and (managed DB) `DATABASE_*`.
