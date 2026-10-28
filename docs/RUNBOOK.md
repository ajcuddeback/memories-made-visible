# Runbook: your own server, now and after AWS

Two parts. **Part A** puts the site live today on a server you already run, with nginx + Let's Encrypt for TLS,
Postgres and photo storage (MinIO) on the same box, and metrics into your existing Grafana. **Part B** moves
photos (and optionally the database) to AWS later without downtime. `docs/DEPLOYMENT.md` has the background for
each piece; this is the ordered checklist.

Assumptions: Ubuntu-ish server with Docker, nginx and certbot already on it; the domain's `A` records can be
pointed at it; you have the Namecheap mailbox details. Replace `www.memoriesmadevisible.com` if the domain differs.

---

## Part A — go live on your server

### A1. Get the code and build the images

CI publishes images to GHCR only from `main` and `v*` tags. Until this branch is merged, build on the server;
it takes a few minutes and needs about 2 GB free.

```bash
sudo mkdir -p /opt/mmv && sudo chown $USER /opt/mmv && cd /opt/mmv
git clone https://github.com/ajcuddeback/memories-made-visible.git .
git checkout claude/modest-brahmagupta-hh6u1h      # or main once merged
cp .env.example .env
```

### A2. Fill in `.env`

Generate the secrets first:

```bash
openssl rand -base64 48      # APP_JWT_SECRET
openssl rand -hex 24         # POSTGRES_PASSWORD
openssl rand -hex 24         # MINIO_ROOT_PASSWORD
```

Then set these (everything else in `.env.example` can keep its default):

```ini
# accounts — created on first boot only, then managed in Studio → Team
ADMIN_OWNER=Austin
ADMIN_AUSTIN_PASSWORD=<strong>
ADMIN_AUSTIN_EMAIL=<your email>
ADMIN_ERICA_PASSWORD=<strong>
ADMIN_ERICA_EMAIL=<her email>
APP_JWT_SECRET=<openssl rand -base64 48>

# site
APP_SITE_URL=https://www.memoriesmadevisible.com
APP_CORS_ORIGINS=https://www.memoriesmadevisible.com
APP_TIME_ZONE=America/New_York
CONTACT_EMAIL=hello@memoriesmadevisible.com
CONTACT_PHONE=
CONTACT_INSTAGRAM=

# email (Namecheap Private Email; app password if 2FA is on)
NOTIFY_TO=bookings@memoriesmadevisible.com
NOTIFY_FROM=admin@memoriesmadevisible.com
NOTIFY_CUSTOMER_FROM=hello@memoriesmadevisible.com
NOTIFY_CUSTOMER=false
SMTP_HOST=mail.privateemail.com
SMTP_PORT=587
SMTP_USERNAME=hello@memoriesmadevisible.com
SMTP_PASSWORD=<mailbox password>
SMTP_AUTH=true
SMTP_STARTTLS=true

# database (bundled container)
POSTGRES_DB=mmv
POSTGRES_USER=mmv
POSTGRES_PASSWORD=<openssl rand -hex 24>

# photos (bundled MinIO, S3-compatible)
MINIO_ROOT_USER=mmv
MINIO_ROOT_PASSWORD=<openssl rand -hex 24>
S3_ENDPOINT=http://storage:9000
S3_PATH_STYLE=true
S3_BUCKET=mmv-photos
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=mmv                       # = MINIO_ROOT_USER
AWS_SECRET_ACCESS_KEY=<MINIO_ROOT_PASSWORD> # = MINIO_ROOT_PASSWORD
S3_PUBLIC_BASE_URL=https://www.memoriesmadevisible.com/photos/mmv-photos

# ports on this box (nginx owns 80/443)
HTTP_PORT=8080
MANAGEMENT_PORT=9091
```

`chmod 600 .env` when done.

### A3. Start the stack

```bash
docker compose -f docker-compose.prod.yml --profile storage up -d --build
docker compose -f docker-compose.prod.yml --profile storage ps      # db, storage healthy; storage-init exited 0
docker compose -f docker-compose.prod.yml logs -f backend             # wait for "Started MmvApplication", Ctrl-C
```

Smoke test from the server:

```bash
curl -s localhost:8080/healthz                       # ok
curl -s localhost:8080/api/pricing | head -c 200     # JSON
curl -s localhost:9091/actuator/prometheus | grep mmv_bookings_pending
```

What is listening: `8080` (site + API, all interfaces, nginx proxies to it), `127.0.0.1:9091` (metrics),
`127.0.0.1:9000` (MinIO). Postgres is not published. If the box has a public firewall, only 80 and 443 need to be
open; close 8080 from the outside (`ufw deny 8080` or a security group rule) so traffic only enters through nginx.

### A4. nginx + Let's Encrypt

```bash
sudo tee /etc/nginx/sites-available/mmv >/dev/null <<'NGINX'
server {
    listen 80;
    server_name memoriesmadevisible.com www.memoriesmadevisible.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://www.memoriesmadevisible.com$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name memoriesmadevisible.com;
    # ssl_certificate lines are added by certbot
    return 301 https://www.memoriesmadevisible.com$request_uri;
}

server {
    listen 443 ssl http2;
    server_name www.memoriesmadevisible.com;
    # ssl_certificate lines are added by certbot

    client_max_body_size 26m;          # studio photo uploads

    # Photos: MinIO on loopback, cached by browsers for a month
    location /photos/ {
        proxy_pass         http://127.0.0.1:9000/;
        proxy_set_header   Host $host;
        proxy_hide_header  x-amz-request-id;
        expires            30d;
        add_header         Cache-Control "public";
    }

    # Everything else: the frontend container (which proxies /api to the backend itself)
    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
NGINX
sudo mkdir -p /var/www/certbot
sudo ln -sf /etc/nginx/sites-available/mmv /etc/nginx/sites-enabled/mmv
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d memoriesmadevisible.com -d www.memoriesmadevisible.com
```

Point the domain's `A` records (`@` and `www`) at the server before running certbot. Certbot installs a renewal
timer; check with `systemctl list-timers | grep certbot`.

`X-Forwarded-For` matters: the backend's rate limiter keys on the client address resolved from it, and the proxy is
on a private address so Tomcat trusts it automatically.

### A5. Prometheus + Grafana

Add the scrape job to your Prometheus config and reload it:

```yaml
  - job_name: mmv-backend
    scrape_interval: 30s
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["127.0.0.1:9091"]
```

(Prometheus in a container: target `host.docker.internal:9091` with `extra_hosts: ["host.docker.internal:host-gateway"]`.)
Then Grafana → Dashboards → New → Import → upload `docs/grafana/mmv-backend.json`, choose the Prometheus data
source. Alerts worth adding: `increase(mmv_emails_total{outcome="failed"}[1h]) > 0`, `up{job="mmv-backend"} == 0`.

### A6. Backups (cron, nightly)

```bash
sudo tee /opt/mmv/backup.sh >/dev/null <<'SH'
#!/bin/bash
set -e
cd /opt/mmv
D=$(date +%F); OUT=/opt/mmv/backups; mkdir -p $OUT
docker compose -f docker-compose.prod.yml exec -T db pg_dump -U mmv mmv | gzip > $OUT/db-$D.sql.gz
docker run --rm -v mmv_photos:/data -v $OUT:/out alpine tar czf /out/photos-$D.tgz -C /data .
find $OUT -mtime +14 -delete
SH
sudo chmod +x /opt/mmv/backup.sh
(crontab -l 2>/dev/null; echo "15 3 * * * /opt/mmv/backup.sh >> /opt/mmv/backups/backup.log 2>&1") | crontab -
```

Copy `/opt/mmv/backups` off the box (rclone to any cloud drive, or `scp` to another machine). A backup that only
lives on the server it protects isn't one. The volume name is `mmv_photos` if the compose project is named `mmv`
(the folder name); check with `docker volume ls`.

### A7. First sign-in and go-live checks

- [ ] `https://www.memoriesmadevisible.com/` loads; `/pricing` shows prices.
- [ ] `/studio` → sign in as Austin → Team shows Erica and Austin with the right access.
- [ ] Upload a photo in Studio → Photos, Publish, confirm its URL starts with `/photos/mmv-photos/` and loads.
- [ ] Set booking hours (Calendar → Booking hours), mark who is bookable (Team).
- [ ] Submit a real request from `/book` → email arrives at `bookings@` (check spam the first time).
- [ ] Approve it → the customer email arrives and names the photographer.
- [ ] Grafana panel "Up" shows 1 and "Requests per second" moves when you browse.
- [ ] `robots.txt` and `sitemap.xml` resolve; submit the sitemap in Google Search Console.
- [ ] Backup cron ran once (`ls /opt/mmv/backups`).
- [ ] Flip `NOTIFY_CUSTOMER=true` once SPF/DKIM are confirmed, if customers should get acknowledgements.

### A8. Updating later

```bash
cd /opt/mmv && git pull
docker compose -f docker-compose.prod.yml --profile storage up -d --build
```

Flyway applies new migrations on start. Everyone signed into the studio needs to sign in again after an update that
changes the token format (the security hardening release did). Rollback: `git checkout <previous commit>` and run
the same command; the database schema is forward-only, so take a backup before updating.

---

## Part B — migrating to AWS

Do photos first (B1); it is the one that changes the public site. The database (B2) is optional; the bundled
Postgres is fine indefinitely for a studio-sized site as long as backups leave the box.

### B1. Photos: MinIO → S3

1. **Bucket + user.** S3 → Create bucket `mmv-photos` in `us-east-1`, keep *Block all public access* on.
   IAM → user `mmv-backend`, programmatic access, inline policy allowing `s3:PutObject`, `s3:GetObject`,
   `s3:DeleteObject` on `arn:aws:s3:::mmv-photos/*` plus `s3:ListBucket` on the bucket itself (for the
   migration). Save the access key and secret.

2. **CloudFront** (recommended) → distribution with the bucket as origin via Origin Access Control; apply the
   bucket policy it prints. Note the domain, e.g. `d1234.cloudfront.net`. Skip this and the backend falls back to
   12-hour presigned URLs (works, slower, not cacheable).

3. **Copy the objects** while the site keeps running (MinIO is still serving):
   ```bash
   docker run --rm --network mmv_default -e MC_HOST_src=http://mmv:$MINIO_ROOT_PASSWORD@storage:9000 \
     -e MC_HOST_aws=https://<AWS_KEY>:<AWS_SECRET>@s3.amazonaws.com minio/mc \
     mirror --overwrite src/mmv-photos aws/mmv-photos
   ```
   Re-run it right before the switch to catch uploads made in between. (The network name is `<folder>_default`;
   `docker network ls` shows it.)

4. **Switch the backend.** In `.env`:
   ```ini
   S3_ENDPOINT=
   S3_PATH_STYLE=false
   AWS_ACCESS_KEY_ID=<aws key>
   AWS_SECRET_ACCESS_KEY=<aws secret>
   S3_PUBLIC_BASE_URL=https://d1234.cloudfront.net      # or blank for presigned URLs
   ```
   Then `docker compose -f docker-compose.prod.yml up -d backend`. Photo URLs are computed from the stored object
   key on every request, so already-published photos move with the setting; no database change.

5. **Verify**: open the site, confirm image URLs now point at CloudFront; upload a new photo in the studio and
   publish it. Then **retire MinIO**: `docker compose -f docker-compose.prod.yml --profile storage stop storage`,
   remove the `/photos/` block from nginx, and drop the photo line from `backup.sh`. Keep the volume for a couple of
   weeks (`docker volume rm mmv_photos` when you're sure).

6. **Backups**: enable S3 versioning on the bucket (Properties → Bucket Versioning) and you have photo history for
   free; add `aws s3 cp /opt/mmv/backups/db-$D.sql.gz s3://mmv-backups/` to `backup.sh` for the database dumps.

### B2. Database: bundled Postgres → RDS (optional)

1. RDS → Create database → PostgreSQL 16, `db.t4g.micro`, 20 GB gp3, **not** publicly accessible if the server is
   in the same VPC, otherwise publicly accessible with the security group limited to the server's IP. Note the
   endpoint, master user and password.
2. Dump and restore (a minute of write-freeze; do it off-hours):
   ```bash
   cd /opt/mmv
   docker compose -f docker-compose.prod.yml stop backend
   docker compose -f docker-compose.prod.yml exec -T db pg_dump -U mmv --no-owner --no-acl mmv > /tmp/mmv.sql
   docker run --rm -i --network host postgres:16-alpine \
     psql "postgresql://<user>:<pass>@<endpoint>:5432/mmv?sslmode=require" < /tmp/mmv.sql
   ```
   (Create the `mmv` database on RDS first if the master database isn't named that.)
3. In `.env` add
   ```ini
   DATABASE_URL=jdbc:postgresql://<endpoint>:5432/mmv?sslmode=require
   DATABASE_USERNAME=<user>
   DATABASE_PASSWORD=<pass>
   ```
   and in `docker-compose.prod.yml` remove the three `DATABASE_*` lines under `backend.environment` (they
   override `.env`) and the `depends_on: db`. Start it: `docker compose -f docker-compose.prod.yml up -d backend`.
   Flyway sees the schema already at the latest version and does nothing.
4. Verify sign-in, bookings and Insights load, then `stop db`. Keep the `pgdata` volume for a few weeks. RDS
   automated backups replace the `pg_dump` line in `backup.sh`; keep a weekly manual dump anyway.

### B3. Cost sanity (monthly, us-east-1, studio-sized traffic)

| Piece | Rough cost |
| --- | --- |
| S3 for a few GB of resized photos + CloudFront | under $2 |
| RDS `db.t4g.micro` 20 GB | about $15 |
| Everything on your existing server (Part A) | $0 extra |

Photos on S3 + CloudFront is worth doing when you get there: it takes storage and bandwidth off your box. RDS is
only worth it if you'd rather pay than run `backup.sh`.
