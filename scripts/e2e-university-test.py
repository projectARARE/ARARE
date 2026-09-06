#!/usr/bin/env python3
"""
ARARE End-to-End University Regression Test
===========================================
Seeds a realistic university through the running application's real import
APIs (canonical file names, fixed insertion order from GET /import/order),
runs the Timefold solver, and validates the resulting timetable.

Data scale is driven by --scale:
    small   8 depts, 64 batches,  48 rooms, ~4K students   (sanity, fast)
    medium  8 depts, 128 batches, 100 rooms, ~8K students
    large   8 depts, 512 batches, 528 rooms, ~51K students (50K/500+ claim)

Teachers are provisioned to the exact teaching load (minimal headcount, each
qualified for the subjects they are assigned), and subject weekly hours are
tuned so every batch fills ~6 of 7 daily slots (1 free slot/day), producing a
dense real-world timetable rather than a sparse one.

The script drives ONLY the backend over HTTP and makes NO changes to the
application: it uses the committed config/behaviour as-is. Slot numbers are
1-based (the committed domain validates slotNumber > 0); seed capacity is
sized so a fresh university is guaranteed feasible.

Usage:
    python scripts/e2e-university-test.py [--scale medium] [--gen-seconds N]

Requires: requests (pip install requests), backend running on :8080.
"""

import argparse
import io
import json
import math
import sys
import time
import zipfile

import requests

DEFAULT_BASE = "http://localhost:8080/api/v1"

ENTITY_ORDER = [
    "timeslots", "buildings", "departments", "rooms",
    "subjects", "teachers", "batches",
]

DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]

# 1-based slot numbers (committed app enforces slotNumber > 0).
# 7 CLASS slots/day + 1 BREAK at slot 3 (10:00-10:30) -> config index 2.
DAY_SLOTS = [
    (1, "08:00", "09:00", "CLASS"),
    (2, "09:00", "10:00", "CLASS"),
    (3, "10:00", "10:30", "BREAK"),
    (4, "10:30", "11:30", "CLASS"),
    (5, "11:30", "12:30", "CLASS"),
    (6, "12:30", "13:30", "CLASS"),
    (7, "13:30", "14:30", "CLASS"),
    (8, "14:30", "15:30", "CLASS"),
]

# Timefold spends this long per solve (request-level override of the 30s
# committed default). The construction heuristic evaluates the full cartesian
# product of (teacher x room x timeslot) per session at ~8k calc/sec, so the
# budget must comfortably cover CH initializing EVERY session before local
# search (CH ~1.4s/step with a scoped solve => ~5 min for ~200 sessions).
SOLVER_BUDGET_SEC = {"small": 480, "medium": 720, "large": 1200}

TIMESLOT_TOKENS = {}  # (day,start,end) -> "DAY@HH:mm-HH:mm"

# Research-paper telemetry collected across one run. Written as JSON at the end.
METRICS = {}


def csv_dump(headers, rows):
    out = [",".join(headers)]
    for r in rows:
        out.append(",".join("" if v is None else str(v) for v in r))
    return "\n".join(out) + "\n"


def slot_type(day, start, end):
    for _, s, e, t in DAY_SLOTS:
        if s == start and e == end:
            return t
    return "UNKNOWN"


class Checks:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.failures = []

    def ok(self, name, cond, detail=""):
        tag = "PASS" if cond else "FAIL"
        print(f"  [{tag}] {name}" + (f"  ({detail})" if detail else ""))
        if cond:
            self.passed += 1
        else:
            self.failed += 1
            self.failures.append(f"{name}: {detail}")

    def summary(self):
        print(f"\n  RESULTS: {self.passed} passed, {self.failed} failed")
        if self.failures:
            print("\n  Failed checks:")
            for f in self.failures:
                print(f"    - {f}")
        return 0 if self.failed == 0 else 1

    def hard_fail(self, name, reason):
        self.passed -= 0
        self.failed += 1
        self.failures.append(name)
        print(f"  [ERROR] {name}: {reason}")


class Api:
    def __init__(self, base):
        self.base = base

    def req(self, method, path, **kw):
        return requests.request(method, self.base + path, timeout=180, **kw)

    def json(self, method, path, expected=(200, 201), **kw):
        r = self.req(method, path, **kw)
        if r.status_code not in expected:
            body = ""
            try:
                body = r.json()
            except Exception:
                body = r.text[:300]
            raise RuntimeError(f"{method.upper()} {path} -> {r.status_code}: {body}")
        return r.json() if r.content else None

    def get(self, path, **kw):
        return self.json("get", path, **kw)

    def post(self, path, **kw):
        return self.json("post", path, **kw)

    def delete(self, path, **kw):
        return self.req("delete", path, **kw)


def reset_derived_state(api):
    """Delete every generated/curriculum-derived row so runs are reproducible.

    Leftover SubjectOfferings, TeacherAssignments, ClassSessions or Schedules
    leak into the NEXT run's curriculum resolution (batchTakesSubject sees an
    explicit offering list and only generates those subjects), silently
    shrinking the session count and wrecking the batch-density numbers. Master
    data (depts, subjects, teachers, batches, rooms, timeslots) is left intact
    and re-upserted by the seed phase.
    """
    print("\n=== Reset derived state ===")
    n = 0
    for path in ("/subject-offerings", "/teacher-assignments", "/events", "/pre-allocations"):
        try:
            for row in api.get(path):
                r = api.delete(f"{path}/{row['id']}")
                if r.status_code in (200, 201, 202, 204):
                    n += 1
        except Exception as ex:
            print(f"    [skip] {path}: {str(ex)[:120]}")
    for sched in api.get("/schedules"):
        sid = sched["id"]
        try:
            for cs in api.get(f"/schedules/{sid}/sessions"):
                api.delete(f"/class-sessions/{cs['id']}")
                n += 1
            r = api.delete(f"/schedules/{sid}")
            if r.status_code in (200, 201, 202, 204):
                n += 1
        except Exception as ex:
            print(f"    [skip] schedule {sid}: {str(ex)[:120]}")
    print(f"    deleted {n} derived rows")


def ensure_institute(api):
    """Departments need a non-null institute; create one when missing."""
    existing = api.get("/institutes")
    if existing:
        return existing[0]
    inst = api.post("/institutes", json={
        "name": "ARARE Institute of Technology",
        "code": "ARARE",
        "description": "Seeded by the E2E regression script.",
    })
    print(f"    created institute id={inst.get('id')} code={inst.get('code')}")
    return inst


def save_university_config(api):
    cfg = api.post("/university-config", json={
        "daysPerWeek": 6,
        "timeslotsPerDay": 8,
        "maxClassesPerDay": 6,
        "breakSlotIndices": [2],
        "workingDays": DAYS,
    })
    print(f"    days={cfg.get('daysPerWeek')} slots/day={cfg.get('timeslotsPerDay')}")
    return cfg


def build_datasets(scale):
    cfg = {
        "small":   {"sections": 2,  "buildings": 8,  "rooms_per_building": 9,
                    "student_base": 60},
        "medium":  {"sections": 4,  "buildings": 10, "rooms_per_building": 14,
                    "student_base": 60},
        "large":   {"sections": 16, "buildings": 12, "rooms_per_building": 44,
                    "student_base": 100},
    }[scale]
    sections = cfg["sections"]
    n_buildings = cfg["buildings"]
    rooms_per_building = cfg["rooms_per_building"]
    student_base = cfg["student_base"]
    batches_per_dept = 4 * sections
    teacher_max_weekly = 30
    # per-batch weekly profile: (weeklyHours, chunkHours) per subject S1..S6 + lab.
    # Total 6+5+6+5+4+5+2 = 33h over 5 working days => ~6.6h/day => at least
    # 33 of 35 class slots per week filled (only 1-2 free slots left).
    subj_profile = [(6, 2), (5, 1), (6, 2), (5, 1), (4, 1), (5, 1)]
    max_batch = max(student_base + ((year * 7 + s * 3) % 6)
                    for year in range(1, 5) for s in range(sections))

    buildings = [f"Building {i+1}" for i in range(n_buildings)]
    departments = [f"D{i+1:02d}" for i in range(8)]
    dept_names = {
        "D01": "Computer Science", "D02": "Electronics", "D03": "Mechanical",
        "D04": "Civil", "D05": "Electrical", "D06": "Information Tech",
        "D07": "Chemical", "D08": "Aerospace",
    }

    # subjects: (dept, code, name, weekly, chunk, rtype, isLab, labtype, minGap, maxPerDay)
    subjects = []
    for dept in departments:
        for j in range(6):
            weekly, chunk = subj_profile[j]
            subjects.append((dept, f"{dept}-S{j+1}", f"{dept_names[dept]} Subject {j+1}",
                             weekly, chunk, "LECTURE", False, "", 1, 2))
        subjects.append((dept, f"{dept}-L1", f"{dept_names[dept]} Lab",
                         2, 2, "LAB", True, "GENERAL_LAB", 1, 1))

    batches = []
    for dept in departments:
        for year in range(1, 5):
            for s in range(sections):
                sec = chr(ord("A") + s)
                student_count = student_base + ((year * 7 + s * 3) % 6)
                free_day = DAYS[(int(dept[1:]) + year + s) % 6]
                batches.append((dept, year, sec, student_count, free_day))

    data = {}

    # ---- timeslots.csv -------------------------------------------------
    rows = []
    for day in DAYS:
        for slot_no, start, end, ttype in DAY_SLOTS:
            rows.append([day, start, end, str(slot_no), ttype])
            TIMESLOT_TOKENS[(day, start, end)] = f"{day}@{start}-{end}"
    data["timeslots.csv"] = csv_dump(
        ["day", "startTime", "endTime", "slotNumber", "type"], rows)

    # ---- buildings.csv -------------------------------------------------
    rows = [[b, f"Campus {i % 3 + 1}"] for i, b in enumerate(buildings)]
    data["buildings.csv"] = csv_dump(["name", "location"], rows)

    # ---- departments.csv (buildingNames embedded) ----------------------
    rows = []
    for i, dept in enumerate(departments):
        allowed = ";".join(buildings[i % n_buildings:i % n_buildings + 2])
        rows.append([dept, dept_names[dept], allowed])
    data["departments.csv"] = csv_dump(["code", "name", "buildingNames"], rows)

    # ---- rooms.csv (availableTimeslots embedded) ------------------------
    lecture_caps = [80, 100, 120, 150, 180, 200, 240]
    all_class_tokens = ";".join(
        tok for (d, s, e), tok in TIMESLOT_TOKENS.items()
        if slot_type(d, s, e) == "CLASS")
    rows = []
    lab_per_building = 2
    for b in buildings:
        for i in range(rooms_per_building - lab_per_building):
            cap = lecture_caps[i % len(lecture_caps)]
            rows.append([b, f"L{i+1:03d}", "LECTURE", "", cap, all_class_tokens])
        for i in range(lab_per_building):
            lab_cap = max_batch + (i * 15)
            rows.append([b, f"LAB-{i+1:02d}", "LAB", "GENERAL_LAB", lab_cap, all_class_tokens])
    data["rooms.csv"] = csv_dump(
        ["buildingName", "roomNumber", "type", "labSubtype", "capacity",
         "availableTimeslots"], rows)

    # ---- subjects.csv ---------------------------------------------------
    rows = [
        [dept, code, name, weekly, chunk, rtype,
         labtype if lab else "", str(lab).lower(), "true", "true", min_gap, max_per_day]
        for dept, code, name, weekly, chunk, rtype, lab, labtype, min_gap, max_per_day in subjects]
    data["subjects.csv"] = csv_dump(
        ["departmentCode", "code", "name", "weeklyHours", "chunkHours",
         "roomTypeRequired", "labSubtypeRequired", "isLab", "requiresTeacher",
         "requiresRoom", "minGapBetweenSessions", "maxSessionsPerDay"], rows)

    # ---- teachers.csv (subjectCodes + availableTimeslots + preferredFreeDay) --
    avail_by_day = {}
    for day in DAYS:
        avail_by_day[day] = [
            tok for (d, s, e), tok in TIMESLOT_TOKENS.items()
            if d == day and slot_type(day, s, e) == "CLASS"
        ]
    first_names = ["Arun", "Priya", "Vikram", "Sunita", "Rajesh", "Meera", "Sanjay",
                   "Kavita", "Anil", "Pooja", "Deepak", "Neha", "Suresh", "Riya",
                   "Manoj", "Asha", "Ravi", "Divya", "Amit", "Pallavi"]
    last_names = ["Sharma", "Verma", "Patel", "Singh", "Kumar", "Reddy", "Nair",
                  "Mishra", "Gupta", "Joshi", "Rao", "Iyer", "Das", "Banerjee"]

    teacher_rows = []
    teacher_meta = []
    emp_seq = 1

    def emit(emp_seq, dept, code, free_day_salt, allowed, max_weekly):
        eid = f"EMP-{emp_seq:04d}"
        name = f"{first_names[emp_seq % len(first_names)]} {last_names[(emp_seq * 3) % len(last_names)]}"
        free_day = DAYS[(free_day_salt + emp_seq - 1) % 6]
        avail_tokens = ";".join(
            tok for d2 in DAYS if d2 != free_day for tok in avail_by_day[d2])
        teacher_rows.append([eid, name, 6, max_weekly, 3, 1,
                             free_day, f"{dept}:{code}", avail_tokens,
                             ";".join(allowed)])
        teacher_meta.append((eid, dept))
        return eid

    for i, dept in enumerate(departments):
        needs = {}
        for j in range(6):
            weekly, _ = subj_profile[j]
            n = math.ceil(weekly * batches_per_dept / (teacher_max_weekly * 0.85))
            needs[f"{dept}-S{j+1}"] = max(3, n)
        n = math.ceil(2 * batches_per_dept / (teacher_max_weekly * 0.85))
        needs[f"{dept}-L1"] = max(2, n)
        allowed = buildings[i % n_buildings:i % n_buildings + 2]
        # Extra teachers: ~25% buffer over the exact load (min 2 per dept), so
        # heads are not all at 100% and the solver has assignment choice.
        buffer = max(2, math.ceil(sum(needs.values()) * 0.25))
        for code, n in needs.items():
            for _ in range(n):
                emit(emp_seq, dept, code, 0, allowed, teacher_max_weekly)
                emp_seq += 1
        extra_codes = list(needs.keys())
        for _ in range(buffer):
            code = extra_codes[(_ + i) % len(extra_codes)]
            emit(emp_seq, dept, code, 1, allowed, teacher_max_weekly)
            emp_seq += 1
    data["teachers.csv"] = csv_dump(
        ["employeeId", "name", "maxDailyHours", "maxWeeklyHours",
         "maxConsecutiveClasses", "movementPenalty", "preferredFreeDay",
         "subjectCodes", "availableTimeslots", "preferredBuildingNames"],
        teacher_rows)

    # ---- batches.csv (workingDays embedded) ------------------------------
    rows = []
    for dept, year, sec, student_count, free_day in batches:
        working = ";".join(d for d in DAYS if d != free_day)
        rows.append([dept, year, sec, student_count, free_day, working])
    data["batches.csv"] = csv_dump(
        ["departmentCode", "year", "section", "studentCount", "preferredFreeDay",
         "workingDays"], rows)

    summary = {
        "buildings": n_buildings,
        "rooms": n_buildings * rooms_per_building,
        "departments": len(departments),
        "subjects": len(subjects),
        "teachers": len(teacher_rows),
        "batches": len(batches),
        "students": sum(b[3] for b in batches),
        "sections_per_year": sections,
    }
    return data, summary


def seed_sequence(api, data, checks):
    print("\n=== Seed data (sequence mode) ===")
    for entity in ENTITY_ORDER:
        content = data[f"{entity}.csv"]
        resp = api.post(f"/import/csv/{entity}", json={
            "csvContent": content, "dryRun": False})
        created = resp.get("created", 0)
        updated = resp.get("updated", 0)
        errors = resp.get("errors", [])
        print(f"    {entity}: created={created} updated={updated}"
              + (f" errors={len(errors)}" if errors else ""))
        for e in errors[:3]:
            print(f"      ERR: {e}")
        checks.ok(f"seed {entity}", not errors,
                  f"created={created} updated={updated} errors={len(errors)}")
        if errors:
            return False
    return True


def seed_zip(api, data, checks):
    print("\n=== Seed data (ZIP mode) ===")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for fname, content in data.items():
            zf.writestr(fname, content)
    buf.seek(0)
    r = api.req("post", "/import/zip",
                files={"file": ("arare-seed.zip", buf, "application/zip")},
                params={"dryRun": "false"})
    if r.status_code not in (200, 201):
        print(f"    POST /import/zip -> {r.status_code}: {r.text[:400]}")
        checks.ok("seed zip", False, f"HTTP {r.status_code}")
        return False
    res = r.json()
    ok_all = True
    for fname, stats in res.get("fileStats", {}).items():
        errs = stats.get("errors", [])
        print(f"    {fname}: created={stats.get('created')} updated={stats.get('updated')}"
              f" skipped={stats.get('skipped')} errors={len(errs)}")
        for e in errs[:3]:
            print(f"      ERR: {e}")
        if errs:
            ok_all = False
    checks.ok("seed zip", ok_all, f"{len(res.get('fileStats', {}))} files")
    return ok_all


def validate_seed(api, summary, checks):
    print("\n=== Validate seeded data ===")
    expectations = {
        "institutes": 1, "departments": summary["departments"],
        "buildings": summary["buildings"], "rooms": summary["rooms"],
        "subjects": summary["subjects"], "teachers": summary["teachers"],
        "batches": summary["batches"],
    }
    for entity, expected in expectations.items():
        try:
            data = api.get(f"/{entity}")
            actual = len(data)
        except Exception as ex:
            actual = -1
            print(f"    GET /{entity} failed: {ex}")
        checks.ok(f"count {entity}", actual >= expected, f"actual={actual} expected={expected}")
        if entity == "rooms":
            if expected >= 500:
                checks.ok("500+ rooms (500+ concurrent classes)", actual >= 500,
                          f"rooms={actual}")
            else:
                print(f"    (500+ room capacity check deferred: {actual} rooms < 500 at this scale)")
    return True


def resolve_scope(api, dept, scale="small"):
    """Department-scoped value ranges that keep the construction-heuristic
    cartesian product small enough to initialize EVERY session within the solve
    budget. The committed gateway scopes value ranges by explicit ids:

      teachers  = EXACTLY the per-subject headcount the data was provisioned
                  with (max(3 | 2-lab, ceil(demand / 25.5h))), i.e. qualified
                  core staff, not the whole buffer of spares.
      rooms     = the dept's allowed-building lecture rooms (bounded pool, by
                  capacity) + the dept's lab rooms.

    Growth: CH step cost = teachers x rooms x 42 slots and ~8k calc/sec, so an
    unbounded scope leaves sessions uninitialized (-init score) even with big
    budgets -- the -45init/-92hard at 360s was exactly that."""
    dept_id = dept["id"]
    teacher_max_weekly = 30
    subj_lecture = api.get("/subjects")
    subjects = {s["id"]: s for s in subj_lecture
                if s.get("departmentId") == dept_id}
    batches_count = sum(1 for b in api.get("/batches")
                        if b.get("departmentId") == dept_id)
    teachers = api.get("/teachers")
    tid_by_subject = {}
    for t in teachers:
        for sid in t.get("subjectIds", []):
            if sid in subjects:
                tid_by_subject.setdefault(sid, []).append(t["id"])
    teacher_ids = []
    for sid, subj in subjects.items():
        weekly = subj["weeklyHours"]
        demand = weekly * batches_count
        n = (max(3, math.ceil(demand / (teacher_max_weekly * 0.85)))
             if not subj.get("isLab")
             else max(2, math.ceil(demand / (teacher_max_weekly * 0.85))))
        ordered = sorted(tid_by_subject.get(sid, []))
        teacher_ids.extend(ordered[:n])
    teacher_ids = sorted(set(teacher_ids))

    pool = {"small": 10, "medium": 12, "large": 16}[scale]
    allowed_building_ids = {b["id"] for b in dept.get("buildingsAllowed", [])}
    rooms = [r for r in api.get("/rooms")
             if r.get("buildingId") in allowed_building_ids]
    lab = sorted((r["id"] for r in rooms if r.get("type") == "LAB"),
                 key=lambda i: i)[:2]
    lecture = sorted(
        (r for r in rooms if r.get("type") == "LECTURE"),
        key=lambda r: (-(r.get("capacity") or 0), r["id"]))[:pool]
    room_ids = [r["id"] for r in lecture] + lab
    print(f"    scope: teachers={len(teacher_ids)} rooms={len(room_ids)}"
          f" (lecture_pool={len(lecture)} lab={len(lab)})")
    return teacher_ids, room_ids


def _validate_capacity_math(api, dept, teacher_ids, checks):
    """Provisioning adequacy: for every subject of the department, the combined
    weekly capacity of the qualified teachers allotted to it (headcount x
    maxWeeklyHours) must be >= the total weekly hours required across the
    batches that take it. This is the "multiplication must cover the class
    requirement" guarantee the data must satisfy by construction."""
    dept_id = dept["id"]
    teachers = {t["id"]: t for t in api.get("/teachers") if t["id"] in teacher_ids}
    teacher_subjects = {}
    for tid, t in teachers.items():
        for sid in t.get("subjectIds", []):
            teacher_subjects.setdefault(sid, []).append(t["maxWeeklyHours"])
    subjects = {s["id"]: s for s in api.get("/subjects")
                if s.get("departmentId") == dept_id}
    batches = [b for b in api.get("/batches") if b.get("departmentId") == dept_id]
    shortages = []
    row_info = []
    for sid, subj in subjects.items():
        demand = subj["weeklyHours"] * len(batches)
        capacity = sum(teacher_subjects.get(sid, [0]))
        headcount = len(teacher_subjects.get(sid, []))
        row_info.append(f"{subj['code']}: {len(batches)}x{subj['weeklyHours']}h ->"
                        f" demand={demand}h vs {headcount} teachers x"
                        f" {subj['chunkHours']}ch = {capacity}h")
        if capacity < demand:
            shortages.append(subj["code"])
    for r in row_info:
        print(f"    {r}")
    checks.ok("teacher capacity >= subject demand (every subject)",
              not shortages, f"{len(shortages)} subjects short: {shortages}")
    METRICS["capacityMath"] = {"rows": row_info,
                               "shortSubjects": shortages}


def feasibility(api, dept, gen_seconds, teacher_ids, room_ids, checks):
    print("\n=== Feasibility check ===")
    try:
        fc_body = {
            "name": "e2e feasibility",
            "scope": "DEPARTMENT",
            "departmentId": dept["id"],
            "teacherIds": teacher_ids,
            "roomIds": room_ids,
        }
        if gen_seconds:
            fc_body["solvingTimeSeconds"] = gen_seconds
        fc = api.post("/schedules/feasibility-check", json=fc_body)
    except Exception as ex:
        checks.hard_fail("feasibility check runs", str(ex)[:200])
        return None
    checks.ok("feasibility check runs", fc.get("feasible") is not None,
              f"errors={fc.get('errorCount')} warnings={fc.get('warningCount')}"
              f" sessions~={fc.get('totalSessionsEstimate')}")
    checks.ok("feasible (no ERROR issues)", bool(fc.get("feasible")),
              str(fc.get("issues", []))[:300])
    METRICS["feasibility"] = {
        "feasible": fc.get("feasible"),
        "errorCount": fc.get("errorCount"),
        "warningCount": fc.get("warningCount"),
        "totalSessionsEstimate": fc.get("totalSessionsEstimate"),
        "availableTimeslots": fc.get("availableTimeslots"),
        "teacherCount": fc.get("teacherCount"),
        "roomCount": fc.get("roomCount"),
        "recommendedSolvingTimeSeconds": fc.get("recommendedSolvingTimeSeconds"),
    }
    return fc


def run_schedule_and_validate(api, dept, gen_seconds, scale, checks):
    t0_scope = time.perf_counter()
    teacher_ids, room_ids = resolve_scope(api, dept, scale)
    METRICS["scope"] = {"teacherIds": len(teacher_ids), "roomIds": len(room_ids),
                        "resolveMs": round((time.perf_counter() - t0_scope) * 1000)}

    _validate_capacity_math(api, dept, teacher_ids, checks)

    fc = feasibility(api, dept, gen_seconds, teacher_ids, room_ids, checks)
    if fc is None or not fc.get("feasible"):
        return

    # Solver budget defaults to the backend-computed recommendation for this
    # exact scope (sessions x teachers x rooms x slots); an explicit
    # --gen-seconds always wins.
    if gen_seconds is None:
        gen_seconds = fc.get("recommendedSolvingTimeSeconds") or SOLVER_BUDGET_SEC[scale]
        print(f"    adopting backend-recommended solver budget: {gen_seconds}s")
    METRICS["solverBudgetSeconds"] = gen_seconds

    print("\n=== Generate schedule ===")
    try:
        job = api.json("post", "/schedules/generate", expected=(200, 201, 202), json={
            "name": f"E2E Schedule ({scale})",
            "scope": "DEPARTMENT",
            "departmentId": dept["id"],
            "teacherIds": teacher_ids,
            "roomIds": room_ids,
            "solvingTimeSeconds": gen_seconds,
        })
    except Exception as ex:
        checks.ok("submit generate", False, str(ex)[:200])
        return
    checks.ok("submit generate", job.get("id") is not None,
              f"jobId={job.get('id')} status={job.get('status')}")
    job_id = job["id"]

    print("\n=== Poll solve job ===")
    terminal = {"SUCCEEDED", "FAILED", "CANCELLED"}
    result = None
    t0 = time.time()
    while time.time() - t0 < gen_seconds + 420:
        time.sleep(2)
        try:
            fresh = api.get(f"/solve-jobs/{job_id}")
        except Exception:
            continue
        status = fresh.get("status", "?")
        score = fresh.get("bestScore") or fresh.get("score") or "-"
        print(f"    [{status}] score={score} elapsed={time.time() - t0:.0f}s")
        if status in terminal:
            result = fresh
            break
    if result is None:
        checks.ok("solve job completes", False, "timed out")
        return
    checks.ok("solve job SUCCEEDED", result["status"] == "SUCCEEDED",
              str(result.get("errorMessage") or "")[:200])
    if result["status"] != "SUCCEEDED":
        return
    schedule_id = result.get("scheduleId")
    checks.ok("schedule created", schedule_id is not None, f"scheduleId={schedule_id}")
    if schedule_id is None:
        return
    METRICS["solveJob"] = {
        "status": result.get("status"),
        "bestScore": result.get("bestScore") or result.get("score"),
        "elapsedMillis": result.get("elapsedMillis"),
        "scheduleId": schedule_id,
    }

    print("\n=== Validate schedule ===")
    schedule = api.get(f"/schedules/{schedule_id}")
    checks.ok("schedule status DRAFT (feasible solution)",
              schedule.get("status") == "DRAFT",
              f"status={schedule.get('status')} score={schedule.get('score')}")
    METRICS["schedule"] = {
        "status": schedule.get("status"),
        "score": schedule.get("score"),
    }

    sessions = api.get(f"/schedules/{schedule_id}/sessions")
    checks.ok("sessions generated", len(sessions) > 0, f"{len(sessions)} sessions")
    if not sessions:
        return
    METRICS["sessions"] = {
        "total": len(sessions),
        "assignedTeacher": sum(1 for s in sessions if s.get("teacherId")),
        "assignedRoom": sum(1 for s in sessions if s.get("roomId")),
        "assignedTimeslot": sum(1 for s in sessions if s.get("timeslotId")),
    }

    teacher_timeslot = {}
    room_timeslot = {}
    batch_timeslot = {}
    teacher_days = {}
    teacher_sess = {}
    conflicts = {"teacher": 0, "room": 0, "batch": 0}
    for s in sessions:
        t = s.get("teacherId"); r = s.get("roomId")
        b = s.get("batchId"); d = s.get("day"); ts = s.get("timeslotId")
        if t and ts:
            k = (t, ts)
            conflicts["teacher"] += teacher_timeslot.get(k, 0)
            teacher_timeslot[k] = True
        if r and ts:
            k = (r, ts)
            conflicts["room"] += room_timeslot.get(k, 0)
            room_timeslot[k] = True
        if b and ts:
            k = (b, ts)
            conflicts["batch"] += batch_timeslot.get(k, 0)
            batch_timeslot[k] = True
        if t and d:
            teacher_days.setdefault(t, set()).add(d)
            teacher_sess[t] = teacher_sess.get(t, 0) + 1
    checks.ok("no teacher double-booking", conflicts["teacher"] == 0,
              f"{conflicts['teacher']} conflicts")
    checks.ok("no room double-booking", conflicts["room"] == 0,
              f"{conflicts['room']} conflicts")
    checks.ok("no batch double-booking", conflicts["batch"] == 0,
              f"{conflicts['batch']} conflicts")

    skewed = [t for t in teacher_days
              if teacher_sess[t] >= 3 and len(teacher_days[t]) < 2]
    checks.ok("teachers spread across days (no <2-day teacher with 3+ sessions)",
              len(skewed) == 0, f"{len(skewed)} skewed of {len(teacher_days)} teachers")

    _validate_timetable_density(api, dept, sessions, checks)

    try:
        expl = api.get(f"/schedules/{schedule_id}/score-explanation")
        feas = expl.get("feasible")
        hard = expl.get("hardScore")
        checks.ok("score explanation feasible + no hard violations",
                  bool(feas) and hard == 0,
                  f"feasible={feas} hard={hard} score={expl.get('score')}")
        METRICS["scoreExplanation"] = {
            "score": expl.get("score"),
            "feasible": feas,
            "hardScore": hard,
            "breakdown": [
                {"constraint": c.get("constraintName"), "level": c.get("level"),
                 "matchCount": c.get("matchCount"), "impact": c.get("scoreImpact")}
                for c in expl.get("constraints", [])
            ],
        }
    except Exception as ex:
        checks.ok("score explanation", False, str(ex)[:120])


def _validate_timetable_density(api, dept, sessions, checks):
    print("    --- timetable density ---")
    teachers = {t["id"]: t for t in api.get("/teachers")}
    batches = {b["id"]: b for b in api.get("/batches")
               if b.get("departmentId") == dept["id"]}

    mismatched = []
    for s in sessions:
        tid = s.get("teacherId"); sid = s.get("subjectId")
        if tid and sid and tid in teachers and sid not in teachers[tid].get("subjectIds", []):
            mismatched.append((s.get("subjectName"), s.get("teacherName")))
    checks.ok("teacher-subject exact match (qualified)",
              len(mismatched) == 0, f"{len(mismatched)} mismatches")

    slot_fill = {}
    for s in sessions:
        b = s.get("batchId")
        if b and b in batches:
            slot_fill[b] = slot_fill.get(b, 0) + (s.get("duration") or 1)
    if slot_fill:
        coverage = list(slot_fill.values())
        fill_ok = all(c >= 33 for c in coverage)
        checks.ok("every batch fills >=33 of 35 weekly slots",
                  fill_ok, f"min={min(coverage)} avg={sum(coverage)/len(coverage):.1f} of 35")
        METRICS["density"] = {"batchSlotCoverageMin": min(coverage),
                              "batchSlotCoverageAvg": round(sum(coverage)/len(coverage), 1),
                              "ofWeekSlots": 35}
    else:
        checks.ok("every batch fills >=33 of 35 weekly slots", False, "no batch sessions")

    batch_daily = {}
    for s in sessions:
        b = s.get("batchId"); d = s.get("day")
        if b and b in batches and d:
            key = (b, d)
            batch_daily[key] = batch_daily.get(key, 0) + 1
    over_daily = [k for k, n in batch_daily.items() if n > 6]
    checks.ok("batch classes/day <= 6 (university config)",
              not over_daily, f"{len(over_daily)} days over 6")

    on_free_day = [s for s in sessions
                   if s.get("batchId") in batches
                   and s.get("day") == batches[s["batchId"]].get("preferredFreeDay")]
    checks.ok("no sessions on batch preferred-free day",
              len(on_free_day) == 0, f"{len(on_free_day)} sessions on free day")

    load_wk = {}
    load_day = {}
    for s in sessions:
        tid = s.get("teacherId"); d = s.get("day")
        dur = s.get("duration") or 1
        if tid:
            load_wk[tid] = load_wk.get(tid, 0) + dur
            if d:
                k = (tid, d)
                load_day[k] = load_day.get(k, 0) + dur
    wk_over = [t for t, v in load_wk.items() if v > teachers[t]["maxWeeklyHours"]]
    day_over = [t for (t, d), v in load_day.items() if v > teachers[t]["maxDailyHours"]]
    checks.ok("teacher loads within maxDaily/maxWeekly",
              not wk_over and not day_over,
              f"weeklyOver={len(wk_over)} dailyOver={len(day_over)}")


def validate_exports(api, schedule_id, checks):
    print("\n=== Export checks ===")
    checks_out = []
    export_metrics = {}

    r = api.req("get", f"/schedules/{schedule_id}/export/csv",
                params={"view": "ALL"})
    csv_ok = r.status_code == 200 and len(r.content) > 0 and r.headers.get("Content-Type", "").startswith("text/csv")
    checks.ok("export CSV", csv_ok, f"HTTP {r.status_code} bytes={len(r.content)}")
    checks_out.append(("csv", r))
    export_metrics["csv"] = len(r.content)

    r = api.req("get", f"/schedules/{schedule_id}/export/pdf")
    pdf_ok = r.status_code == 200 and r.content.startswith(b"%PDF")
    checks.ok("export PDF", pdf_ok, f"HTTP {r.status_code} bytes={len(r.content)}")
    checks_out.append(("pdf", r))
    export_metrics["pdf"] = len(r.content)

    r = api.req("get", f"/schedules/{schedule_id}/export/excel")
    xlsx_ok = r.status_code == 200 and len(r.content) > 0 and r.content[:2] == b"PK"
    checks.ok("export Excel", xlsx_ok, f"HTTP {r.status_code} bytes={len(r.content)}")
    checks_out.append(("xlsx", r))
    export_metrics["excel"] = len(r.content)

    r = api.req("get", "/import/export/zip")
    zip_ok = r.status_code == 200 and len(r.content) > 0 and r.content[:2] == b"PK"
    checks.ok("relational ZIP export", zip_ok, f"HTTP {r.status_code} bytes={len(r.content)}")
    checks_out.append(("zip", r))
    export_metrics["relationalZip"] = len(r.content)

    METRICS["exports"] = export_metrics


def push_assignments_and_offerings(api, dept_id, checks):
    print("\n=== Teacher assignments & subject offerings (sample) ===")
    try:
        teachers = api.get("/teachers")
        batches = api.get("/batches")
        subjects = api.get("/subjects")
        if not teachers or not batches or not subjects:
            checks.ok("assignments/offerings", False, "missing seed data")
            return
        batch = next((b for b in batches if b.get("departmentId") == dept_id), batches[0])
        subject = next((s for s in subjects if s.get("departmentId") == dept_id), subjects[0])
        teacher = next((t for t in teachers if subject["id"] in t.get("subjectIds", [])),
                       teachers[0])

        ta = api.post("/teacher-assignments", json={
            "teacherId": teacher["id"],
            "subjectId": subject["id"],
            "batchId": batch["id"],
            "weeklyHours": 4,
            "priority": 1,
            "notes": "E2E sample",
        })
        so = api.post("/subject-offerings", json={
            "subjectId": subject["id"],
            "batchId": batch["id"],
            "weeklyHours": 4,
            "elective": False,
        })
        messages = f"assignment={ta.get('id')} offering={so.get('id')}"
        checks.ok("assignments/offerings", True, messages)
    except Exception as ex:
        body = str(ex)
        already = "Conflict" in body and "already allotted" in body
        checks.ok("assignments/offerings", already,
                  ("idempotent 409 (already allotted)" if already
                   else f"{body[:200]}"))


def main():
    ap = argparse.ArgumentParser(description="ARARE E2E university regression test")
    ap.add_argument("--base", default=DEFAULT_BASE, help="Backend base URL")
    ap.add_argument("--scale", choices=["small", "medium", "large"], default="medium",
                    help="Data scale to generate")
    ap.add_argument("--seed", choices=["sequence", "zip"], default="sequence")
    ap.add_argument("--gen-seconds", type=int, default=None,
                    help="Solver budget in seconds (request-level, overrides committed default)")
    ap.add_argument("--metrics-out", default=None,
                    help="Write research-paper metrics JSON to this path")
    args = ap.parse_args()

    gen_seconds = args.gen_seconds  # None => adopt the backend recommendation

    budget_label = f"{gen_seconds}s" if gen_seconds else "auto (backend-recommended)"

    print("=" * 70)
    print("  ARARE End-to-End University Regression Test")
    print(f"  scale={args.scale} seed={args.seed} solver-budget={budget_label}")
    print("=" * 70)

    METRICS.update({
        "scale": args.scale,
        "seedMode": args.seed,
        "solverBudgetSeconds": gen_seconds,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    })
    t_start = time.perf_counter()

    checks = Checks()
    api = Api(args.base)

    # Phase 0: connectivity + order
    try:
        order = api.get("/import/order")
        published = [step.get("name") for step in order]
    except Exception as ex:
        checks.hard_fail("backend reachable", str(ex)[:200])
        return checks.summary()
    checks.ok("backend reachable", True, f"HTTP via {args.base}")
    checks.ok("import order canonical", published == ENTITY_ORDER, published)

    # Config + institutes
    print("\n=== University config ===")
    try:
        save_university_config(api)
        checks.ok("university config saved", True, "days=6 slots/day=8")
    except Exception as ex:
        checks.ok("university config saved", False, str(ex)[:200])
        return checks.summary()

    print("\n=== Institutes ===")
    try:
        inst = ensure_institute(api)
        checks.ok("institute available", inst is not None,
                  f"id={inst.get('id') if inst else None}")
    except Exception as ex:
        checks.ok("institute available", False, str(ex)[:200])
        return checks.summary()

    # Derived data leaks between runs (offerings/assignments/schedules), which
    # silently rewrites curricula and shrinks session counts. Reset it once
    # before seeding so every run is reproducible.
    try:
        reset_derived_state(api)
    except Exception as ex:
        print(f"    warn: derived-state reset incomplete: {str(ex)[:120]}")

    # Build + seed
    data, summary = build_datasets(args.scale)
    print("\n=== Seed data ===")
    print(f"    dataset: {summary}")

    if args.seed == "zip":
        seeded = seed_zip(api, data, checks)
    else:
        seeded = seed_sequence(api, data, checks)
    if not seeded:
        return checks.summary()

    validate_seed(api, summary, checks)

    # Solve one department
    print("\n=== Solve department D01 (Computer Science) ===")
    dept = None
    try:
        for d in api.get("/departments"):
            if d.get("code") == "D01":
                dept = d
                break
    except Exception as ex:
        checks.hard_fail("resolve D01 department", str(ex)[:200])
    if dept is None:
        checks.hard_fail("resolve D01 department", "not found")
        return checks.summary()

    run_schedule_and_validate(api, dept, gen_seconds, args.scale, checks)

    # Exports + relational layer
    try:
        schedules = api.get("/schedules")
        latest = schedules[-1] if schedules else None
        if latest and latest.get("id"):
            validate_exports(api, latest["id"], checks)
        else:
            checks.ok("export checks", False, "no schedule available")
    except Exception as ex:
        checks.ok("export checks", False, str(ex)[:200])

    push_assignments_and_offerings(api, dept["id"], checks)

    METRICS["totalElapsedSeconds"] = round(time.perf_counter() - t_start, 3)
    if args.metrics_out:
        with open(args.metrics_out, "w") as fh:
            json.dump(METRICS, fh, indent=2)
        print(f"\nMetrics written to {args.metrics_out}")

    return checks.summary()


if __name__ == "__main__":
    sys.exit(main())