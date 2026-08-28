# ARARE Deployment

ARARE is designed to run **on a university's own infrastructure** (single
deployment, single tenant) on a trusted local network or private cloud. There is
**no authentication layer by design** — do not expose the API publicly without a
reverse proxy / network policy that restricts access.

---

## 1. Database (PostgreSQL)

1. Install PostgreSQL 14+.
2. Create the database (Flyway does **not** create it by default):
   ```sql
   CREATE DATABASE araredb;
   ```
3. Create a role with read/write access:
   ```sql
   CREATE ROLE arare WITH LOGIN PASSWORD 'strong-password';
   GRANT ALL PRIVILEGES ON DATABASE araredb TO arare;
   ```
4. On startup, Flyway applies all migrations under
   `src/main/resources/db/migration` (`V1`…`V11`) and validates the schema
   (`spring.jpa.hibernate.ddl-auto=validate`). No manual schema work is needed.

> Opt-in auto-create: set `ARARE_FLYWAY_AUTO_CREATE_DB=true` and the app will
> issue `CREATE DATABASE` against the `postgres` maintenance DB on first boot
> (dev/ephemeral only). Not recommended for production.

---

## 2. Environment variables

| Variable | Default | Required | Description |
| --- | --- | --- | --- |
| `ARARE_DB_URL` | `jdbc:postgresql://localhost:5432/araredb` | no* | JDBC URL (*DB must exist) |
| `ARARE_DB_USERNAME` | `postgres` | no | DB username |
| `ARARE_DB_PASSWORD` | — | **yes** | DB password (default 123456 � dev only, override for real use) |
| `ARARE_CORS_ORIGINS` | `http://localhost:5173` | no | Comma-separated allowed origins |
| `ARARE_FLYWAY_AUTO_CREATE_DB` | `false` | no | Auto-create DB on startup (dev only) |

Example (systemd / shell):
```bash
export ARARE_DB_URL=jdbc:postgresql://db.internal:5432/araredb
export ARARE_DB_USERNAME=arare
export ARARE_DB_PASSWORD='*****'
export ARARE_CORS_ORIGINS='https://timetable.university.edu'
```

---

## 3. Running the application

### Build the JAR
```bash
mvn clean package        # produces target/arare-*.jar
```

### Run the JAR
```bash
java -jar target/arare-0.0.1-SNAPSHOT.jar
```
The server listens on **port 8080** and serves the REST API under `/api/v1`.

### Or run via Maven
```bash
mvn spring-boot:run
```

### Frontend
Build the React app and serve the static files from a web server (nginx, etc.),
pointing `VITE_BACKEND_URL` at the backend, then set `ARARE_CORS_ORIGINS` to the
frontend's origin:
```bash
cd frontend
npm install
npm run build            # outputs dist/
# serve dist/ via nginx / static host on the origin used in ARARE_CORS_ORIGINS
```

---

## 4. CORS

`config/WebConfig` allows `ARARE_CORS_ORIGINS` (default `http://localhost:5173`)
for `/api/**` with credentials enabled. Because credentials are allowed, the
origin list must be explicit — **never use `*`**. Set `ARARE_CORS_ORIGINS` to the
exact frontend origin(s), comma-separated:
```
ARARE_CORS_ORIGINS=https://timetable.university.edu,https://admin.university.edu
```

---

## 5. Async solver (solveTaskExecutor)

Schedule generation and disruption partial-resolves run on a dedicated executor
(`config/AsyncConfig`):

- **core pool size:** 2
- **max pool size:** 4
- **queue capacity:** 100
- **rejected policy:** `CallerRunsPolicy` (caller runs when saturated)
- **graceful shutdown:** `waitForTasksToCompleteOnShutdown=true`,
  `awaitTerminationSeconds=60`

Tune these only if you routinely run many concurrent large solves. The solver
time limit defaults to **30s** (`timefold.solver.termination.spent-limit`) and
can be overridden per request via `ScheduleRequest.solvingTimeSeconds`.

The worker opens **no DB connection while solving**; it reads the problem in a
short read-only transaction, solves in memory, then persists the result in one
short write transaction. A `SolveJobRecoverySweeper` marks any `QUEUED`/`RUNNING`
jobs `FAILED` on startup so a crash cannot leave a job blocking schedule deletion
forever.

---

## 6. PostgreSQL vs H2

- **Production / local deployment:** PostgreSQL (the only supported relational
  store; `postgresql` driver is a runtime dependency).
- **Tests:** H2 in-memory (`test` scope) with the same Flyway migrations, so
  `mvn test` needs no external database.

Do not use H2 for production — it is not the validated schema target and lacks
Postgres-specific behavior.

---

## 7. Operational notes

- **Single active config:** keep exactly one `UniversityConfig` row active
  (`POST /api/v1/university-config` replaces it). Changing it and re-generating
  produces a new schedule version.
- **Single active schedule per institute scope:** activating a schedule archives
  any other `ACTIVE` schedule in the same `instituteId` scope.
- **Network isolation:** because there is no auth, restrict port 8080 (and the
  database port) to the trusted network / reverse proxy only.
- **Logs:** `logging.level.org.hibernate.SQL=INFO` is on by default; reduce in
  production if needed.
- **Backups:** back up the PostgreSQL `araredb` database; all schedules,
  sessions, and config live there.
