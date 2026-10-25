# Memories Made Visible

Website and studio admin for Memories Made Visible Photography (Polk County, FL).

| Part | Stack | Path |
| --- | --- | --- |
| Public site + hidden admin | Angular 22 (standalone, signals, zoneless) | `frontend/` |
| API | Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA · Flyway | `backend/` |
| Database | PostgreSQL 16 | `docker-compose.yml` |
| Photo storage | Amazon S3 (MinIO locally) | `backend/.../photo` |
| Email | SMTP via Spring Mail (Mailpit locally) | `backend/.../notification` |

## What's in the site

**Public** — `/`, `/portfolio`, `/events`, `/pricing`, `/book`, `/about`. Dark/light theme toggle. Fully responsive.

**Studio admin** — `/studio`. Not linked from anywhere on the site; share the URL with the two studio accounts only.
Sections: Overview (revenue + next up), Requests (approve / decline / quote), Calendar, Pop-ups (with print sales),
Galleries (delivery pipeline), Pricing (edits publish straight to the site), Photos (upload to S3 into named site slots).

**Booking requests** from `/book` are stored as `PENDING` and immediately emailed to the studio inbox
(`NOTIFY_TO`), with an optional acknowledgement to the customer (`NOTIFY_CUSTOMER=true`). The form has a honeypot
and a per-IP rate limit.

## Run it locally

Prereqs: Node 24 (or ≥ 22.22.3), Java 21, Docker.

```bash
# 1. Infrastructure: Postgres, MinIO (S3), Mailpit (SMTP + inbox UI)
docker compose up -d

# 2. API — the dev profile seeds demo bookings/pop-ups and uses MinIO + Mailpit
cd backend
AWS_ACCESS_KEY_ID=minio AWS_SECRET_ACCESS_KEY=minio123 ./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. Site — proxies /api to the backend on :8080
cd frontend
npm install
npm start          # http://localhost:4200
```

Dev admin logins: **Erica / erica-dev**, **Austin / austin-dev** (see `backend/src/main/resources/application-dev.yml`).
Booking emails land in Mailpit at http://localhost:8025. Uploaded photos land in the MinIO console at http://localhost:9001.

### Tests

```bash
cd backend && ./gradlew test      # Spring Boot slice tests on H2 (mail + S3 mocked)
cd frontend && npm run build      # production build (type-checks templates)
```

## Configuration

Everything the API needs is an environment variable — see [`.env.example`](.env.example). The important ones:

| Variable | Purpose |
| --- | --- |
| `ADMIN_ERICA_PASSWORD`, `ADMIN_AUSTIN_PASSWORD` | Admin passwords; upserted (bcrypt) on every boot, so changing one and restarting is the reset flow |
| `APP_JWT_SECRET` | ≥ 32 chars; signs admin sessions (12 h) |
| `NOTIFY_TO` | Studio inbox for booking requests (**TBD** — leave blank and requests are stored but a warning is logged instead of an email) |
| `SMTP_*` | Mail server (SES SMTP, Google Workspace, Postmark…) |
| `S3_BUCKET`, `AWS_REGION`, `S3_PUBLIC_BASE_URL` | Photo bucket; with a public base URL (CloudFront or bucket site) links are plain, otherwise the API issues 12-hour presigned links |
| `APP_CORS_ORIGINS`, `APP_SITE_URL` | Site origin for CORS and for the admin link inside emails |

The price list is seeded from `PricingDefaults.java` on first boot; after that the admin owns it ("Reset to defaults" restores it).
Photo slot ids are defined once in `PhotoSlots.java` and referenced by the Angular pages.

## Deploying

- **Frontend**: `npm run build` → static files in `frontend/dist/frontend/browser`. Serve from S3 + CloudFront, Netlify, or
  behind the same reverse proxy as the API. Route every unknown path to `index.html`, and forward `/api/*` to the backend.
- **Backend**: `./gradlew bootJar` → `backend/build/libs/mmv-backend-*.jar`. Needs Java 21, a Postgres URL, and the env above.
  Flyway runs migrations on start. Health check: `GET /actuator/health`.
- **S3**: one bucket, block public ACLs, and either put CloudFront in front (recommended) or enable bucket-policy reads on the
  `site/` prefix. The IAM identity needs `s3:PutObject`, `s3:DeleteObject`, `s3:GetObject` on that bucket.

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
