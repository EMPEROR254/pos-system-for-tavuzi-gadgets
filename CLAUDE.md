# POS System for Tavuzi Gadgets - guide for Claude

Built by BS Codes for TAVUZI GADGETS. Point of Sale (POS) system for TAVUZI GADGETS. Electronics shop: Accessories sale, Electronics(tv, laptops, phones) sales, imei tracking, warranty management and repair services and offices on call repairs

## Read first

The approved specification for this project:

- `docs/spec.md`
- `docs/UI_CHECKLIST.md (UI and speed checklist - follow it)`

## Design

Follow the client's designs in docs/ (design spec and images). Where they say nothing, follow docs/UI_CHECKLIST.md.

## Components

| Folder | Stack | Test | Build |
|---|---|---|---|
| `.` | Java 21 + Spring Boot 3 (Maven) | `mvn -q test` | `mvn -q -DskipTests package` |
| `src/frontend` | React + Vite + TypeScript (web frontend) (Default) | `npm test` | `npm run build` |

The web app in `src/frontend` is part of the Spring Boot project: `npm run build` there writes the finished site into `src/main/resources/static`, which Spring serves (`config/SpaConfig.java` sends page URLs to the app). All API endpoints live under `/api/**`. In development the frontend dev server runs on its own port and calls Spring Boot on :8080. In the security configuration, permit the static files and page URLs and protect `/api/**`.

## Build rules (BS Codes)

- Never write real secrets (API keys, passwords, tokens, M-Pesa credentials) into any file. Use environment variables and keep `.env.example` up to date with placeholder values.
- Never commit `.env` or other secret files; keep them in `.gitignore`.
- After every change, run the component's test command and fix failures before finishing.
- Stay inside this project folder. Do not change files elsewhere on the computer.
- Do not run destructive commands (deleting data, dropping databases, force-pushing) - they will be refused.
- Keep code readable for an intermediate developer: small files, clear names, a short comment at the top of each module, and comments for the "why" of non-obvious rules.
- Follow the approved specification in docs/. If something in it is unclear or impossible, say so instead of guessing.
- Style every web and mobile UI with Tailwind CSS first (NativeWind on React Native). Add shadcn/ui components only where a ready-made component clearly helps. No other CSS frameworks or CSS-in-JS.
- Every UI must look polished and modern - never plain white pages: light, dark and system themes with a toggle, subtle animations that respect reduced motion, and fast loading. Follow docs/UI_CHECKLIST.md and check it before finishing a milestone.
- Every screen must work on a phone (360 px wide) as well as a laptop. Business systems include an admin area (dashboard with Recharts charts, management screens, reports with export) for the owner/manager roles.

## Shared modules (tested - reuse them, don't rewrite them)

- **Login, roles and permissions** in `src/main/java/ke/bscodes/possystemfortavuzigadgetsbackend/security`: Password hashing, signed access tokens (HS256) and a role-to-permission map (owner, manager, cashier, stock clerk, accountant). Env: JWT_SECRET.
- **M-Pesa STK Push (Daraja)** in `src/main/java/ke/bscodes/possystemfortavuzigadgetsbackend/mpesa`: Sends an M-Pesa payment prompt to the customer's phone (Lipa na M-Pesa Online) and handles the callback. Env: MPESA_ENVIRONMENT, MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_SHORTCODE, MPESA_PASSKEY, MPESA_CALLBACK_URL.
- **Receipt printing (ESC/POS thermal printers)** in `src/main/java/ke/bscodes/possystemfortavuzigadgetsbackend/receipt`: Builds receipts for 58mm/80mm thermal printers (ESC/POS commands) with VAT and M-Pesa reference, and sends them to a network printer. Env: none.

## Java 21 + Spring Boot 3 - `.`

Structure:

```
src/main/java/<package>/
  users/         one package per feature, e.g. User, UserController, UserService,
                 UserRepository (JPA or custom SQL) and its DTO records
  sales/, ...    the other features, same shape
  reports/       admin dashboard numbers, charts data and report exports
  security/      security filter chain, JWT, password hashing, roles/permissions
  config/        other configuration (OpenAPI, CORS, SpaConfig for the web app)
  common/        error handling (ProblemDetail), small shared helpers
src/frontend/    the web app, when there is one (React + Vite or Next.js)
src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  db/migration/  Flyway SQL migrations V1__init.sql, V2__...
src/test/java/<package>/   JUnit 5 tests; integration tests use Testcontainers PostgreSQL
```

## Spring Boot conventions

### Stack

- Java 21, Spring Boot 3.5, Maven (Gradle only when chosen for the project).
- PostgreSQL with Spring Data JPA; schema changes only through Flyway migrations
  in `src/main/resources/db/migration` (`V1__init.sql`, `V2__add_x.sql`). Never
  use `ddl-auto=update` outside tests.
- Spring Security with stateless JWT (access token 15 min, refresh token 7 days),
  BCrypt password hashing, role-based access with `@PreAuthorize`.
- OpenAPI docs with springdoc (`/swagger-ui.html`), disabled in production unless required.
- Tests: JUnit 5, AssertJ, Mockito; integration tests with Testcontainers PostgreSQL.

### Code structure

- Package by feature: each feature package holds everything about it, e.g.
  `users/` → `User` (entity), `UserController`, `UserService`, `UserRepository`
  (Spring Data JPA, or a custom SQL class with `JdbcClient` when queries are
  complex), plus its DTO records. Same for `sales/`, `products/`, `payments/`.
- `security/` is its own package: the security filter chain, JWT, password
  hashing, roles and permissions. `config/` holds other configuration
  (OpenAPI, CORS, the web app - `SpaConfig`); `common/` holds error handling
  and small shared helpers. Anything else important gets its own package too.
- Keep the number of packages small and obvious; no `impl/`, `helpers/` or deep nesting.
- Controllers are thin: validate input, call a service, map to a response DTO.
- Request/response DTOs are Java `record`s with Bean Validation annotations.
  Never expose JPA entities from controllers.
- Services hold business rules and are `@Transactional` where they change data.
- Constructor injection only (no field `@Autowired`); make dependencies `final`.
- Money is `BigDecimal` with scale 2 (KES); never `double`. Store amounts in
  `numeric(12,2)` columns.
- Time is `Instant` in UTC in the database; convert to `Africa/Nairobi` only for display.

### Errors and logging

- One `@RestControllerAdvice` returns RFC 7807 `ProblemDetail` responses.
- Log with SLF4J; never log secrets, tokens, passwords, full card numbers or
  M-Pesa PINs. Mask phone numbers in logs (`2547****5678`).

### Configuration and secrets

- All secrets come from environment variables (`${MPESA_CONSUMER_KEY}` in
  `application.yml`). Commit `.env.example` with placeholder values only.
- Profiles: `dev` (local PostgreSQL via docker compose) and `prod`.

### Kenya-specific

- Phone numbers stored in E.164 without `+` (`2547XXXXXXXX`); normalise `07…`/`+254…` input.
- M-Pesa callbacks must be idempotent (store `CheckoutRequestID`/`TransID`, ignore repeats)
  and respond quickly; do slow work asynchronously.
- VAT is 16% unless configured otherwise; keep the rate in configuration, not code.

### Admin dashboard and reports

- A `reports/` feature package serves the admin dashboard and reports: aggregated
  queries (totals per day, top products, payment-method mix) with a date range, never
  loading all rows into Java. Use JPQL/SQL aggregates or projections, indexes on date
  and status columns, and a summary table only if the queries get slow.
- Management endpoints are paginated (`Pageable`, max page size) with filters and sorting.
- CSV export streams rows (`text/csv`); admin endpoints are protected with `@PreAuthorize`
  for the admin roles.

### Web app inside this project

- When the project has a web frontend, it lives in `src/frontend` (React + Vite or
  Next.js) and `npm run build` there writes into `src/main/resources/static`
  (generated - not in git). `config/SpaConfig.java` serves it: real files as-is,
  page URLs get the app's `index.html`.
- Every endpoint is under `/api/**`. In `security/`, permit the static files and
  page URLs and protect `/api/**`.
- Speed: responses are compressed (`server.compression` in `application.yml`);
  paginate list endpoints; avoid N+1 queries (fetch joins / projections); add
  indexes for filtered and sorted columns. See `docs/UI_CHECKLIST.md`.

### Quality bar

- Every feature gets unit tests for services and a web or integration test for its endpoints.
- `mvn -q test` must pass before a milestone is considered done.
- Keep classes small and readable; comment the "why" of non-obvious rules.

## React + Vite + TypeScript (web frontend) - `src/frontend`

Structure:

```
src/
  main.tsx, App.tsx      entry, app frame (header + ThemeToggle) and routes
  pages/                 one file or folder per screen (admin/ for the dashboard, management and reports)
  components/            shared UI (Tailwind; shadcn/ui pieces in components/ui)
  services/              API calls - one file per backend area, all via services/api.ts
  utils/                 helpers (formatKes, theme, dates in Africa/Nairobi)
  index.css              Tailwind + theme tokens (brand colour, light/dark) + animations
```

## React + Vite conventions

### Stack
- React 19, Vite, TypeScript `strict`, React Router (routes lazy-loaded).
- Server state with TanStack Query; small global state with Zustand when needed.
- Forms with react-hook-form + zod.
- Tests: Vitest + Testing Library for components and logic.

### Structure
- `src/pages/` - one file (or folder) per screen; `src/components/` - shared UI
  (`components/ui` for shadcn/ui pieces); `src/services/` - every API call
  (one file per area, e.g. `services/sales.ts`, all through `request()` in
  `services/api.ts`), never `fetch` inside components; `src/utils/` - helpers
  (formatting, theme).
- Inside a Spring Boot project (`src/frontend`): the build goes to
  `../main/resources/static` and `/api` is proxied to :8080 in development - keep
  API calls relative (`/api/...`). Standalone: the API base URL is `VITE_API_URL`.
- No secrets in the frontend - anything in the bundle is public.

### UI (see docs/UI_CHECKLIST.md)
- **Tailwind CSS first, always** - no CSS-in-JS, no other CSS frameworks. shadcn/ui
  components (in `components/ui`, plain files we own) only where a ready-made one
  clearly helps: dialogs, dropdowns, date pickers, data tables.
- Polished and modern, never plain white: theme tokens in the global CSS
  (`bg-background`, `text-foreground`, `bg-card`, `border-border`, `text-muted`,
  `bg-brand`) - change the brand colour there, not per screen.
- Light, dark and system themes with the `ThemeToggle` in the header (already in
  the starter); the first-paint script prevents a flash of the wrong theme.
- Subtle motion with Tailwind (`transition`, `animate-fade-in`, `animate-slide-up`,
  150-250 ms); respect reduced motion; never animate busy work screens or big tables.
- Loading skeletons, empty and error states for every list; inline form errors;
  toasts for success.
- Mobile-first: cheap Android phones (360 px) up to 1366×768 laptops; touch
  targets at least 44 px; visible focus; labels on every input.
- Money: `formatKes` (`KES 1,250`), right-aligned with `tabular-nums`.
- Speed: code-split every route, self-host fonts, lazy images, debounce search,
  paginate long lists - first screen under 200 KB of JavaScript (gzipped).
- Swahili/English strings kept in one file when the client wants Swahili.

### Admin area (owner and managers)
- Every business system gets an admin area: a dashboard, management screens and reports,
  shown only to admin roles (checked on the server too).
- Dashboard: key-number cards (today's sales, orders, low stock...) and charts with
  **Recharts** (`ResponsiveContainer` so charts fit any screen; colours from the theme
  tokens so they work in light and dark). Line/area for trends, bar for top items,
  pie/donut only for a few categories.
- Admin routes are lazy-loaded, so Recharts never slows down the everyday screens
  (e.g. the cashier's sale screen).
- Management screens: search, filters, sorting and server-side pagination; edit in a
  dialog or side panel; confirm before deleting.
- Reports: date-range picker with quick ranges (today, this week, this month), filters,
  totals row, and CSV export (PDF when the spec asks).

### Mobile friendly
- Mobile-first from 360 px: the admin sidebar becomes a drawer (hamburger) or bottom
  navigation on phones; tables become stacked cards below `md`; dialogs become full-screen
  sheets on phones; forms are one column with large inputs.
- Check every screen at 360 px, 768 px and 1366 px (browser device mode) before finishing.

### Quality bar
- `npm run lint`, `npm test` and `npm run build` pass before a milestone is done.
