# ARARE Development Setup

This guide covers building and running ARARE locally for development and testing.

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | `java.version=21` in `pom.xml` |
| Maven | 3.9+ | Wrapper not provided; use system Maven |
| Node.js | 18+ | For the `frontend/` React app |
| PostgreSQL | 14+ | Production database (H2 is used automatically for tests) |
| Git | any | — |

No authentication layer is present by design (see `ARCHITECTURE.md` §6); run on a
trusted network.

---

## Backend (Spring Boot)

### Build & test
```bash
# From repo root
mvn clean install          # compile + package
mvn test                   # run backend tests (uses H2 + Flyway)
```

Tests use an in-memory **H2** database (`com.h2database:h2`, `test` scope) with
the same Flyway migrations, so no Postgres is needed to run `mvn test`. Key test
classes live under `src/test/java/com/arare/`, including:

- `features/solver/*Test` — constraint provider, session generation, solution persistence, integration generate
- `features/schedule/ScheduleServiceImplTest`, `ExcelExportServiceTest`, `PdfExportServiceTest`
- `features/solvejob/SolveJobRecoverySweeperTest`, `SolveJobServiceTest`
- `features/dataimport/*Test` — CSV upsert/import/export
- `features/impact/*Test` — disruption dependency graph + impact analysis
- `features/cascadedeletion/CascadeDeletionServiceTest`
- `exception/GlobalExceptionHandlerTest`

### Run (requires PostgreSQL)
```bash
export ARARE_DB_URL=jdbc:postgresql://localhost:5432/araredb
export ARARE_DB_USERNAME=postgres
export ARARE_DB_PASSWORD=*****        # required — no default
# optional:
export ARARE_CORS_ORIGINS=http://localhost:5173
export ARARE_FLYWAY_AUTO_CREATE_DB=true   # only for ephemeral/dev DB auto-create

mvn spring-boot:run
```

The backend listens on **port 8080**.

### Environment variables

| Variable | Default | Required | Meaning |
| --- | --- | --- | --- |
| `ARARE_DB_URL` | `jdbc:postgresql://localhost:5432/araredb` | no (but DB must exist) | JDBC URL |
| `ARARE_DB_USERNAME` | `postgres` | no | DB user |
| `ARARE_DB_PASSWORD` | — | **yes** | DB password (default 123456 � dev only) |
| `ARARE_CORS_ORIGINS` | `http://localhost:5173` | no | Comma-separated allowed origins for `/api/**` |
| `ARARE_FLYWAY_AUTO_CREATE_DB` | `false` | no | If `true`, creates the target DB on the `postgres` maintenance DB at startup (dev only) |

> The target database **must already exist** by default. Flyway runs on startup
> (`spring.flyway.enabled=true`, `baseline-on-migrate=true`) and validates the
> schema (`spring.jpa.hibernate.ddl-auto=validate`). Set
> `ARARE_FLYWAY_AUTO_CREATE_DB=true` only for throwaway/dev setups.

### Code style / conventions
- **Lombok** extensively used (`@Getter/@Setter/@Builder/@RequiredArgsConstructor`).
- **Hand-written DTO mapping** — no MapStruct `@Mapper` is used; services contain
  `toResponse(...)` methods. (A `mapstruct` dependency is declared in `pom.xml`
  but unused.)
- **`@Transactional` discipline** — business logic is transactional; the async
  solve worker deliberately uses short, isolated transactions (see
  `ARCHITECTURE.md` §4).
- **`spring.jpa.open-in-view=false`** — fetch everything you need inside the
  transaction; no lazy loading after the transaction closes.
- **`BaseEntity`** auditing + `@Version` optimistic locking on every entity
  (except `ClassSession`).

---

## Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev          # Vite dev server on http://localhost:5173
```

`vite.config.ts` proxies `/api` → `http://localhost:8080` (override with
`VITE_BACKEND_URL`). So the browser only talks to `:5173`; the API calls hit
`:8080` server-side.

### `.env.example`
```
VITE_BACKEND_URL=http://localhost:8080
```
Copy to `.env` and adjust if the backend runs elsewhere.

### Build & type-check
```bash
npm run build         # tsc && vite build
npm run test          # (if configured) component/unit tests
npx tsc --noEmit      # type-check without emitting
```

The frontend uses React 18, TypeScript 5, TanStack Query, React Router, Zod,
Tailwind. API client lives in `frontend/src/services/api.ts`; types in
`frontend/src/types/index.ts`.

---

## Typical local workflow

1. Start PostgreSQL and create the `araredb` database.
2. `export ARARE_DB_PASSWORD=...` and `mvn spring-boot:run` (backend on :8080).
3. `cd frontend && npm install && npm run dev` (UI on :5173).
4. Open `http://localhost:5173`, configure `UniversityConfig`, load master data
   (CSV import or forms), then generate a schedule.

To run only the backend tests without Postgres: `mvn test` (H2).
