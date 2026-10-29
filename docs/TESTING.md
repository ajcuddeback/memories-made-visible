# Testing

Three layers, each run by CI on every push and pull request (`.github/workflows/ci.yml`). Images are only built once
all three pass.

| Layer | What it covers | Run it |
|---|---|---|
| **Backend integration** (`backend/src/test`, JUnit + MockMvc, H2) | Every API rule: booking validation and the day lock, per-photographer capacity, pricing draft/publish, roles and team management, invites and resets, analytics, emails (captured from a mocked sender). Fast, no services needed. | `cd backend && ./gradlew test` |
| **Frontend build** | The Angular app compiles, type-checks its templates, and prerenders the public routes. | `cd frontend && npm run build` |
| **End-to-end** (`e2e/`, Playwright) | The real stack in a real browser: Spring Boot with the `e2e` profile on a scratch Postgres database, `ng serve` with its `/api` proxy, and a small SMTP server that captures every email so tests can read invites, confirmations and reset links. | `cd e2e && npm test` |

## End-to-end suite

Prerequisites: Java 21, Node 24, a local Postgres with a role `mmv` / password `mmv` that may create databases
(`docker compose up -d` in `backend/` gives you one), and the frontend's `npm install` done.

```bash
cd e2e
npm install
npx playwright install chromium   # once
npm test                           # headless; `npm run test:headed` to watch; `npm run report` for the HTML report
```

What happens on `npm test`:

1. `scripts/run-backend.sh` drops and recreates `mmv_e2e`, then starts the backend with `--spring.profiles.active=e2e`
   (`backend/src/main/resources/application-e2e.yml`: fixed accounts Erica/Austin, no demo data, email to `localhost:2525`).
2. Playwright starts `ng serve` (reusing one that's already running locally) and the SMTP capture (`mail-server.ts`,
   messages readable at `http://127.0.0.1:2526/messages`).
3. The spec files run in order, one worker, sharing that database — later files build on earlier ones (the session
   booked in `02` is approved in `03`, delivered, then assigned in `06`).

| File | Covers |
|---|---|
| `01-public` | Home, nav, footer contact; pricing page (packages, what's included, hourly + add-on); contact card on the booking page; about/events render. |
| `02-booking` | Fixed package end to end with both emails; the taken slot disappears for the next visitor; hourly package with hours and an add-on (quote and slot length); form can't send until complete. |
| `03-sessions` | Approve with the reviewed email (price and photographer in it); decline/restore; shot → editing → proofing → delivered with link and final price; Overview revenue. |
| `04-pricing-publish` | Edit a package, Save (draft, not live), Publish (live, booking page follows), Discard. |
| `05-calendar` | Block a morning through the styled pickers and see the slots vanish; the block on the day view and its removal; closing a weekday in booking hours. |
| `06-team` | Owner/admin full nav; add an employee → invite email with temporary password → forced password change; reduced studio and 403s; assign as lead → email → employee sees only that session and can run it (email action, own hours); forgot-password link; deactivate/reactivate; admin can't touch the owner. |
| `07-photographers` | No step until someone is bookable; per-person block affects only them; "no preference"; request assigned to the chosen photographer; a person's own booking hours apply only to them; hand-off to a busy person refused; unflagging restores the single calendar. |
| `08-insights` | A visit shows up as visitors, pages and the booking funnel; employees get 403. |
| `09-mobile` | iPhone-sized viewport: every public and studio page has no horizontal overflow; the phone calendar and account menu work. |

Failures keep a trace, screenshot and video under `e2e/test-results/`; open a trace with
`npx playwright show-trace <path>/trace.zip`. In CI those are uploaded as the `e2e-report` artifact.

## Adding a test

Put shared helpers in `e2e/tests/helpers.ts` (`signIn`, `waitForMail`, `pickPublicDate`, `goToMonth`, `api`). Prefer
role/label locators (`getByRole('button', { name: 'Publish' })`) over CSS, add `exact: true` when a name is a prefix of
another ("2:00 PM" vs "12:00 PM"), and pick dates with `futureWeekday(n)` using a different `n` per file so files don't
collide on the shared calendar.
