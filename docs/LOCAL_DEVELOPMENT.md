# Local development

Everything needed to run the whole site on your own machine, with an explanation of each moving part.
Nothing in local development touches a real service — no AWS, no Namecheap, no internet beyond package downloads.

## The stack, and why each piece is there

| Piece | What it is | Role in this project |
| --- | --- | --- |
| **Angular 22** (`frontend/`) | Google's TypeScript UI framework. We use the modern style: standalone components, signals for state, zoneless change detection. | The public website *and* the hidden studio admin at `/studio`. Public pages are **prerendered** to static HTML at build time (for SEO and fast first paint) and then "hydrated" in the browser. |
| **Spring Boot 3.5 / Java 21** (`backend/`) | The standard Java web framework. | The REST API under `/api`: bookings, pricing, pop-ups, galleries, photo uploads, admin login, email notifications. |
| **PostgreSQL 16** | Relational database. | Stores everything the admin edits. Tables are created and versioned by **Flyway** migrations in `backend/src/main/resources/db/migration` — the app never alters the schema on its own. |
| **Spring Security + JWT** | Auth layer. | Admin sign-in returns a signed token (12 h); every `/api/admin/*` call carries it. Public endpoints need nothing. |
| **Amazon S3** (production) / **SeaweedFS** (local) | Object storage for photos. | Uploads are resized server-side, stored under `site/<slot>/…`, and served to the site by URL. SeaweedFS is an open-source S3 clone that stands in locally so the code path is identical. |
| **SMTP** (production: Namecheap Private Email) / **Mailpit** (local) | Outbound email. | Every booking request emails the studio inbox. Mailpit is a fake mail server with a web inbox, so you can see exactly what would be sent. |
| **Docker Compose** (`docker-compose.yml`) | Runs Postgres, SeaweedFS and Mailpit as containers. | So you don't install any of them on your machine. The app itself runs outside Docker during development for fast reloads. |
| **Gradle** (`backend/gradlew`) | Java build tool (wrapper included; no install). | Builds, tests and runs the backend. |
| **npm / Angular CLI** | JS build tooling. | `npm start` runs the dev server with live reload; `/api` requests are proxied to the backend (`proxy.conf.json`), so there are no CORS issues locally. |

How the pieces talk locally:

```
browser ──► http://localhost:4200  (Angular dev server)
                └─ /api/* proxied to ──► http://localhost:8080  (Spring Boot)
                                               ├─► Postgres   localhost:5432
                                               ├─► SeaweedFS  localhost:8333  (S3 API)
                                               └─► Mailpit    localhost:1025  (SMTP)
```

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Docker Desktop | any current | Must be running before `docker compose up`. |
| Java | **21** | `java -version`. Temurin/Adoptium is fine. |
| Node.js | **24** (or ≥ 22.22.3) | Angular 22's CLI refuses older versions. Use nvm. |
| Git | any | |

### Windows notes (things that bit us)

- **PowerShell "running scripts is disabled"** when running `npm`: run once
  `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`, or use `npm.cmd`, or use Git Bash.
- **Two Node installs**: if `nvm use 24` says 24 but `node -v` says something else, a standalone Node earlier on `PATH`
  is shadowing nvm. `which -a node` shows both. Uninstall the standalone one (needs admin), or in Git Bash add
  `export PATH="/c/Users/<you>/AppData/Local/<nvm folder>/.nodejs:$PATH"` to `~/.bash_profile` (Git Bash reads
  `.bash_profile`, not `.bashrc`).
- **Setting env vars**: PowerShell `$env:NAME='value'`; cmd `set NAME=value`; Git Bash `NAME=value ./command`.
- Shell scripts mounted into containers must be LF, not CRLF. `.gitattributes` enforces this; if you cloned before it
  existed, run `git add --renormalize .` once.

## Running it

Three terminals, from the repo root.

**1 — Infrastructure**
```bash
docker compose up -d
docker compose ps          # db, s3, mail all "healthy"; s3-init runs once and exits
```

**2 — Backend** (the `dev` profile: demo data, SeaweedFS, Mailpit, dev admin passwords)
```bash
cd backend
# any values work for SeaweedFS; the SDK just needs *something*
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test ./gradlew bootRun --args='--spring.profiles.active=dev'
```
PowerShell: `$env:AWS_ACCESS_KEY_ID='test'; $env:AWS_SECRET_ACCESS_KEY='test'; ./gradlew bootRun --args='--spring.profiles.active=dev'`

First run downloads Gradle and dependencies (a few minutes). Ready when you see `Started MmvApplication`.

**3 — Frontend**
```bash
cd frontend
npm install                # first time, and whenever package.json changes
npm start                  # http://localhost:4200
```

## Where everything is

| What | URL / login |
| --- | --- |
| Website | http://localhost:4200 |
| Studio admin | http://localhost:4200/studio — **Erica / `erica-dev`**, **Austin / `austin-dev`** |
| API | http://localhost:8080/api/… · health: http://localhost:8080/actuator/health |
| Email inbox (Mailpit) | http://localhost:8025 |
| Uploaded photos (SeaweedFS) | http://localhost:8888/buckets/mmv-photos/ |
| Postgres | `localhost:5432`, db/user/password `mmv` / `mmv` / `mmv` |

Dev-only values (passwords, JWT secret, SMTP target, S3 endpoint) live in
`backend/src/main/resources/application-dev.yml`. Production values come from environment variables — see
`.env.example`. You do **not** need a `.env` file for local development.

## A good smoke test

1. Open `/book`, pick a session type, then a date — only days with open times are selectable — and a start time (slots run every 30 minutes inside the studio's booking hours, 7am–6pm by default, and last the session type's length). Fill in details, send.
2. Open Mailpit — you should see the notification to `bookings@…` and the customer acknowledgement.
3. Open `/studio`, sign in as Erica — the request is in **Requests** with a badge. Set a quote, approve it.
4. **Calendar** shows it colour-coded by status with its time. Click a day to see everything on it, **Block time** for appointments or holidays (the note is private; customers just lose those slots), **Move** a session, or open **Booking hours** to change the per-weekday window. Two people can't take the same slot: a booking locks its day's row (`SELECT … FOR UPDATE` on `calendar_day`) while the slot is re-checked and written, and a pending request holds its slot until it's declined. **Overview** includes it too. Once the session date has passed, start its gallery under **Galleries**; the last step, **Deliver & email link**, takes the client's gallery link and the final price (which replaces the quote, so Overview revenue shows what was actually paid) and emails the client a drafted note you can edit first.
5. **Publish** — pricing, pop-ups and website photos are saved as a *draft*. The public site keeps serving the last published version until you press **Publish** in the bar at the top of the studio (or **Discard** to throw the draft away). Bookings and galleries are operational data and take effect immediately.
6. **Pricing** — change a price, Save, Publish; the public `/pricing` page updates. You can also add or remove whole categories. Each category has a "Show in Portfolio" toggle: when on, the Portfolio page gets a filter chip for it (using the optional Portfolio label) and three photo slots appear under Photos and fill in a "What's included" list per package — one item per line — which shows up behind a "See what's included" toggle on the pricing page.
7. **Photos** — drop an image onto a slot; it's resized, stored in SeaweedFS, and appears on the site once you Publish.

## Demo data

The `dev` profile seeds sample bookings, pop-ups and galleries (from the design mock-ups) into an **empty** database
so the admin has something to show. Production never seeds. To start clean: `docker compose down -v` (drops the
Postgres and photo volumes) then `up -d` and restart the backend — it re-seeds.

Bundled defaults that are *not* data: the logo (`frontend/public/images/logo.*`) and three portrait fallbacks for the
Erica/Austin slots. An admin upload to those slots overrides the fallback.

## Tests and checks

```bash
cd backend && ./gradlew test        # Spring Boot tests on H2; mail + S3 mocked
cd frontend && npm run build        # production build + prerender; type-checks every template
```

CI (`.github/workflows/ci.yml`) runs both on every pull request.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `/pricing` says the price list isn't loading | Backend isn't up. Check terminal 2 for `Started MmvApplication`. |
| Backend: `Connection refused … :8333` on photo upload | SeaweedFS not running: `docker compose ps`, then `docker compose up -d`. |
| Backend: `password authentication failed` | Postgres volume from an older config: `docker compose down -v && docker compose up -d`. |
| Port 5432 / 8080 / 4200 already in use | Another Postgres/app is running. Stop it or change the port mapping in `docker-compose.yml`. |
| `ng serve`: "requires a minimum Node.js version" | Wrong Node on PATH — see Windows notes above. |
| `docker compose up` can't pull an image | The image was removed upstream (this happened with MinIO and LocalStack). Check `docker compose logs <service>` and tell whoever maintains the compose file. |
| Admin shows nothing / "Couldn't load the studio data" | Token expired or backend down. Log out and back in. |
