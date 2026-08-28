# ARARE

**A**daptive **R**eal-time **A**nalysis and **R**e-evaluation **E**ngine — a university timetable scheduling platform.

ARAR builds and maintains optimized weekly timetables. Operators load master data, run constraint-based schedule generation (Timefold Solver), inspect scores, handle disruptions/events with partial re-solving, and export the result. It is a Spring Boot backend with a React/TypeScript operator console.

> **Authentication is out of scope by design.** ARARE is intended to run on a university's own infrastructure (local network / single tenant). The REST API is unauthenticated — keep the backend off the public internet and protect it at the network layer or behind a gateway.

---

## What you need to run it

**Prerequisites:** JDK 21, Maven, Node 18+, PostgreSQL.

**1. Database** — create an empty PostgreSQL database (Flyway migrations run on startup):
```bash
createdb araredb
```

**2. Backend** — set the required environment variables, then start:
```bash
export ARARE_DB_URL=jdbc:postgresql://localhost:5432/araredb
export ARARE_DB_USERNAME=postgres
export ARARE_DB_PASSWORD=yourpassword        # optional: defaults to 123456 for local dev; override for real use
# optional: ARARE_CORS_ORIGINS=http://localhost:5173
mvn spring-boot:run
```
The API serves at `http://localhost:8080/api/v1`. A copy-ready template is in [`.env.example`](.env.example).

**3. Frontend** (optional, for the operator console):
```bash
cd frontend
npm install
npm run dev      # Vite dev server on http://localhost:5173 (proxies /api to :8080)
```

**Run the tests:**
```bash
mvn test                 # backend (H2 + Flyway)
cd frontend && npm run build   # frontend type-check + production build
```

---

## Core workflow (operator)

1. Load master data — institutes, departments, buildings, rooms, batches/sections, teachers, subjects, timeslots.
2. Set the active **University Config** (working days, periods/day, max classes/day, break slots).
3. **Generate** a schedule (Timefold solves for ~30s by default). Inspect the score breakdown.
4. **Edit manually** (drag-and-drop) or **handle disruptions/events** → a partial re-solve repairs only the impacted sessions.
5. **Export** to Excel / PDF / CSV.

The timetable viewer supports compact/comfortable density, sticky day/time headers, multiselect filters (department/batch/teacher/room), conflict-only and unplaced-only views, and collapsible side panels — built to stay readable with thousands of sessions.

---

## Documentation

Start here, then follow the links for depth.

| Topic | Doc |
|-------|-----|
| Architecture, layers, async solve pipeline, request flows | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| JPA domain model & enums | [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md) |
| Full REST API reference | [docs/API.md](docs/API.md) |
| Developer setup & testing | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Production / local deployment | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Constraint solver | [docs/algorithms/SCHEDULING_SOLVER.md](docs/algorithms/SCHEDULING_SOLVER.md) |
| Disruption impact analysis | [docs/algorithms/IMPACT_ANALYSIS.md](docs/algorithms/IMPACT_ANALYSIS.md) |
| Pre-allocations | [docs/algorithms/PREALLOCATION.md](docs/algorithms/PREALLOCATION.md) |
| Pre-solve feasibility check | [docs/algorithms/FEASIBILITY_CHECK.md](docs/algorithms/FEASIBILITY_CHECK.md) |
| CSV / relational import | [docs/algorithms/DATA_IMPORT.md](docs/algorithms/DATA_IMPORT.md) |
| Excel / PDF export | [docs/algorithms/EXPORT.md](docs/algorithms/EXPORT.md) |
| Cascade deletion | [docs/algorithms/CASCADE_DELETION.md](docs/algorithms/CASCADE_DELETION.md) |

---

## Tech stack

**Backend:** Java 21 · Spring Boot 3.3 · Spring Data JPA · Flyway · PostgreSQL (prod) / H2 (test) · Timefold Solver 1.14 · Lombok.
**Frontend:** React 18 · TypeScript · Vite · Tailwind CSS · React Query · Recharts.

## Repository layout

```
src/main/java/com/arare/{common,config,exception,features}   backend modules
src/main/resources/db/migration                            Flyway migrations
frontend/src/{pages,components,services}                     React app
docs/                                                        this documentation set
```

## Troubleshooting

- **Generation fails** → run the feasibility check first; verify timeslot topology vs University Config, subject chunking divisibility, teacher qualifications, and room type/capacity coverage.
- **Disruption impact looks too large** → inspect shared-resource density (teacher/room/batch) and the disrupted day/scope.
- **Import fails** → check headers/separators/reference tokens; import foundational entities first (timeslots → buildings → departments → rooms → subjects → teachers → batches).

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for internals.
