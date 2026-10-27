# Memories Made Visible

Website and studio admin for Memories Made Visible Photography (Polk County, FL).

| Part | Stack | Path |
| --- | --- | --- |
| Public site + hidden admin | Angular 22 (standalone, signals, zoneless) | `frontend/` |
| API | Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA · Flyway | `backend/` |
| Database | PostgreSQL 16 | `docker-compose.yml` |
| Photo storage | Amazon S3 (SeaweedFS locally) | `backend/.../photo` |
| Email | SMTP via Spring Mail (Mailpit locally) | `backend/.../notification` |

## What's in the site

**Public** — `/`, `/portfolio`, `/events`, `/pricing`, `/book`, `/about`. Dark/light theme toggle. Fully responsive.
Every public route is **prerendered to static HTML** at build time (Angular SSR, static output mode) and hydrated in the
browser, so crawlers get complete pages and first paint doesn't wait for JavaScript. Page data (pricing, pop-ups, photos)
is fetched from the API in the browser; skeletons hold the layout meanwhile.

**SEO** — per-route titles/descriptions/canonicals/Open Graph (`app.routes.ts` → `SeoService`), `LocalBusiness` JSON-LD
with the Winter Haven address and Polk County service area (`src/index.html`), `robots.txt`, `sitemap.xml`, web manifest.
The canonical domain lives in `src/app/core/site.ts` and in `public/robots.txt` / `public/sitemap.xml` — change all three
if the domain changes.

**Performance** — uploaded photos are resized server-side (1800px max edge, JPEG) before they reach S3; logo is a 9 KB
WebP; above-the-fold fonts are preloaded; the hero image is `fetchpriority=high`, everything else lazy-loads; below-the-fold
home sections use incremental hydration. Lighthouse (mobile, throttled) on the prerendered build: performance 90–98,
SEO 100, accessibility 100, best practices 100, CLS 0.

**Studio admin** — `/studio`. Not linked from anywhere on the site; share the URL with the two studio accounts only.
Sections: Overview (revenue + next up), Sessions (one row per session: request → booked → shot/editing/proofing →
delivered, with the quote, approval/decline emails and the delivery email), Calendar (status-coloured, block time,
booking hours), Pop-ups (with print sales), Pricing (categories, packages, lengths, add-ons — Save, then Publish),
Photos (upload to S3 into named site slots — Publish to go live), Insights (first-party, anonymous site analytics: visitors, time on site, pages, referrers, booking funnel), Team (owner /
admin / employee accounts; invites by email with a temporary password; forgot-password by email).

**Booking requests** from `/book` are stored as `PENDING` and immediately emailed to **bookings@memoriesmadevisible.com**
(`NOTIFY_TO`) from admin@, with an optional acknowledgement to the customer (`NOTIFY_CUSTOMER=true`). The form has a honeypot
and a per-IP rate limit.

## Run it locally

> Full walkthrough with explanations and Windows notes: [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md).
> Production: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).


Prereqs: Node 24 (or ≥ 22.22.3), Java 21, Docker.

```bash
# 1. Infrastructure: Postgres, SeaweedFS (S3), Mailpit (SMTP + inbox UI)
docker compose up -d

# 2. API — the dev profile seeds demo bookings/pop-ups and uses SeaweedFS + Mailpit
cd backend
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test ./gradlew bootRun --args='--spring.profiles.active=dev'
# (PowerShell: $env:AWS_ACCESS_KEY_ID='test'; $env:AWS_SECRET_ACCESS_KEY='test'; ./gradlew bootRun --args='--spring.profiles.active=dev')

# 3. Site — proxies /api to the backend on :8080
cd frontend
npm install
npm start          # http://localhost:4200
```

Dev admin logins: **Erica / erica-dev**, **Austin / austin-dev** (see `backend/src/main/resources/application-dev.yml`).
Booking emails land in Mailpit at http://localhost:8025. Uploaded photos can be browsed at http://localhost:8888/buckets/mmv-photos/.

### Tests

```bash
cd backend && ./gradlew test      # Spring Boot slice tests on H2 (mail + S3 mocked)
cd frontend && npm run build      # production build + prerender (type-checks templates)
```

## Configuration

Everything the API needs is an environment variable — see [`.env.example`](.env.example). The important ones:

| Variable | Purpose |
| --- | --- |
| `ADMIN_ERICA_PASSWORD`, `ADMIN_AUSTIN_PASSWORD`, `ADMIN_*_EMAIL`, `ADMIN_OWNER` | The first accounts, created once (bcrypt) on first boot; after that passwords are changed in the studio and people are managed under Team |
| `APP_JWT_SECRET` | ≥ 32 chars; signs admin sessions (12 h) |
| `NOTIFY_TO` / `NOTIFY_FROM` | Booking requests go to `bookings@memoriesmadevisible.com`, sent from `admin@memoriesmadevisible.com` (defaults; override per environment). Customer acknowledgements come from `NOTIFY_CUSTOMER_FROM` (bookings@) |
| `SMTP_*` | Mail server. Namecheap Private Email: `mail.privateemail.com`, port 587 (STARTTLS), username = the real mailbox (`hello@`), password = its password. `NOTIFY_FROM`/`NOTIFY_TO` can be any alias of that mailbox (`admin@`, `bookings@`) |
| `S3_BUCKET`, `AWS_REGION`, `S3_PUBLIC_BASE_URL` | Photo bucket; with a public base URL (CloudFront or bucket site) links are plain, otherwise the API issues 12-hour presigned links |
| `APP_CORS_ORIGINS`, `APP_SITE_URL` | Site origin for CORS and for the admin link inside emails |

The price list is seeded from `PricingDefaults.java` on first boot; after that the admin owns it ("Reset to defaults" restores it).
Photo slot ids are defined once in `PhotoSlots.java` and referenced by the Angular pages.

## Deploying

### Docker (recommended)

Both apps ship a multi-stage `Dockerfile`:

| Image | Base | Serves |
| --- | --- | --- |
| `backend/Dockerfile` | Temurin 21 JRE (alpine), non-root, layered Spring Boot jar | API on `:8080`, health at `/actuator/health` |
| `frontend/Dockerfile` | nginx-unprivileged (alpine) | Angular build on `:8080`, SPA fallback, `/api/*` proxied to `BACKEND_URL` |

**Single host with Compose** (site + API + Postgres; photos on S3, mail via your SMTP provider):

```bash
cp .env.example .env     # fill in passwords, JWT secret, SMTP, S3, POSTGRES_PASSWORD
docker compose -f docker-compose.prod.yml up -d --build
```

The site is on port 80 (`HTTP_PORT`); put TLS in front of it (Caddy/Traefik/Cloudflare/ALB). Because nginx proxies
`/api`, the browser only ever talks to one origin, so `APP_CORS_ORIGINS` can simply be your site URL.

**Prebuilt images**: the GitHub Actions workflow (`.github/workflows/ci.yml`) runs the tests on every PR and, on `main`
and `v*` tags, pushes `ghcr.io/ajcuddeback/memories-made-visible-backend` and `…-frontend`. Set `BACKEND_IMAGE` /
`FRONTEND_IMAGE` in `.env` to pull those instead of building on the server.

**Managed platforms** (ECS/Fargate, Fly.io, Render, Railway…): run the two images as two services with the env from
`.env.example`; point the frontend's `BACKEND_URL` at the backend service's internal address, and give the backend a
managed Postgres `DATABASE_URL`. The backend picks up AWS credentials from a task/instance role, so `AWS_ACCESS_KEY_ID`
is only needed off-AWS.

### Without Docker

- **Frontend**: `npm run build` → static files in `frontend/dist/frontend/browser`. Serve from S3 + CloudFront, Netlify, or
  behind the same reverse proxy as the API. Route every unknown path to `index.html`, and forward `/api/*` to the backend.
- **Backend**: `./gradlew bootJar` → `backend/build/libs/mmv-backend-*.jar`. Needs Java 21, a Postgres URL, and the env above.
  Flyway runs migrations on start.

### S3

One bucket, block public ACLs, and either put CloudFront in front (recommended, set `S3_PUBLIC_BASE_URL`) or enable
bucket-policy reads on the `site/` prefix. The IAM identity needs `s3:PutObject`, `s3:DeleteObject`, `s3:GetObject` on
that bucket.

## Layout

```
frontend/src/app
├── core/        theme, viewport, public data service, models, date helpers
├── shared/      <app-photo-slot>, <app-icon>
├── site/        shell (header/footer) + home, portfolio, events, pricing, booking, about
└── studio/      auth, interceptor, admin store, shell + login, sections/*
backend/src/main/java/com/memoriesmadevisible
├── auth/        admin users, JWT, login
├── booking/     requests → sessions
├── notification/ booking emails
├── event/       pop-ups + print sales
├── gallery/     delivery pipeline
├── pricing/     session types + packages (+ defaults)
├── photo/       slots, S3 storage, upload
└── seed/        dev-profile demo data
```
