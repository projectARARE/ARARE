# Impact Analysis (Disruption Blast-Radius)

This document describes how ARARE computes the *minimal* set of sessions that a
disruption (teacher absence, room closure, blocked slot, special event, or
cancellation) forces to be rescheduled, and how that set bounds the solver's
freedom during a partial resolve.

All classes live in `features/impact/`.

## 1. Data structures

### `SessionNode` (`impact/SessionNode.java`)

A lightweight, **pure-Java** snapshot of a `ClassSession` used inside the graph.
It holds only IDs – no Hibernate entity references – so the graph never triggers
lazy loading and can be garbage-collected after the request:

```java
record SessionNode(
    Long sessionId,
    Long teacherId, Long roomId, Long batchId, Long sectionId, Long timeslotId,
    String day,      // SchoolDay.name(), e.g. "MONDAY"; null if unassigned
    boolean locked
) {}
```

### `DependencyEdge` (`impact/DependencyEdge.java`)

A directed edge `source → target` tagged with a `DependencyType`. Edges are
logically bidirectional (the builder adds both directions), but stored as two
directed edges so BFS can follow them in one direction per step.

### `DependencyType` (`impact/DependencyType.java`)

`TEACHER`, `ROOM`, `BATCH` – the three shared-resource kinds that propagate a
disruption.

### `DependencyGraph` (`impact/DependencyGraph.java`)

In-memory adjacency-list graph:

* `Map<Long, SessionNode> nodes` keyed by session id.
* `Map<Long, List<DependencyEdge>> adjacency`.
* `addNode`, `addEdge(source, target, type)`, `getNode`, `getNeighbors`,
  `nodeCount`, `edgeCount`.

It is built per request and discarded afterwards (never persisted).

### `DisruptionRequest` / `DisruptionType` (`impact/DisruptionType.java`)

```java
enum DisruptionType {
    TEACHER_UNAVAILABLE, ROOM_UNAVAILABLE, TIMESLOT_BLOCKED,
    SESSION_CANCELLED, SPECIAL_EVENT
}
```

A request carries `type`, `affectedEntityId`, and an optional `date`
(`LocalDate`). For `SPECIAL_EVENT` the entity id is unused (the day is the
scope); for `TEACHER_UNAVAILABLE`/`ROOM_UNAVAILABLE` the entity id is the
teacher/room id; for `TIMESLOT_BLOCKED` it is the timeslot id; for
`SESSION_CANCELLED` it is the single target session id.

## 2. Graph construction — `DependencyGraphBuilder` (`impact/DependencyGraphBuilder.java`)

`build(sessions)` adds one node per session, then three kinds of **day-scoped**
edges:

1. **TEACHER** – group sessions by `teacherId:day`; connect every pair
   bidirectionally.
2. **ROOM** – group by `roomId:day`; connect every pair.
3. **BATCH** – group by `effectiveBatchId:day` (section-aware, so lab-split
   sections group with their parent batch); connect every pair.

Day-scoping is essential: a Monday teacher absence must reach *Monday* sessions
only. The previous implementation grouped by resource id alone, which linked a
teacher's Monday session to their Tuesday session and let a Monday-only
disruption flood the entire week during BFS – contradicting the "minimal set"
promise. Sessions with a `null` timeslot are not linked by teacher/room/batch
edges (they have no day to conflict on); the analyzer still seeds them directly
when appropriate (e.g. a blocked timeslot re-opens all unassigned sessions).

Complexity is O(n²) per resource group, but each group is small (a teacher
typically has 10–20 sessions), so the build is fast.

## 3. BFS traversal — `ImpactAnalyzer` (`impact/ImpactAnalyzer.java`)

`analyze(event, graph, sessions)` returns an ordered `Set<Long>` (BFS order =
closest first):

1. **Seed** (`findInitialSessions`): every session `isDirectlyAffected` by the
   disruption. For `TIMESLOT_BLOCKED`, currently-unassigned sessions are *also*
   seeded, otherwise they could be newly placed into the blocked slot.
2. **Traverse**: pop a node, add to `impacted`. If the node is `locked`, it is
   reported but **expansion stops** (the solver can't move it anyway).
3. **Expand**: for each neighbour edge, if not yet impacted and `shouldExpand`
   returns true, enqueue it. `shouldExpand` currently returns `true` for all
   edge types (all shared resources are considered potentially conflicted);
   it is the single extension point for finer granularity later.

### `isDirectlyAffected` (per `DisruptionType`)

| Type | Match rule |
|------|------------|
| `TEACHER_UNAVAILABLE` | `teacher.id == affectedEntityId` **and** `matchesDay(s, event)` |
| `ROOM_UNAVAILABLE` | `room.id == affectedEntityId` **and** `matchesDay(s, event)` |
| `TIMESLOT_BLOCKED` | `timeslot.id == affectedEntityId` |
| `SESSION_CANCELLED` | `s.id == affectedEntityId` |
| `SPECIAL_EVENT` | `event.date() != null` **and** `matchesDay(s, event)` (day only) |

`matchesDay` returns **false** when `event.date()` is null. This is deliberate:
a dayless teacher/room block produces no `DisruptionConstraintFact` in the
solver, so the preview must also report zero impact and degrade to a no-op
rather than claiming impact and then doing nothing on re-solve.

## 4. `SESSION_CANCELLED` special case

`SESSION_CANCELLED` is handled **directly**, not by BFS:

* `DisruptionServiceImpl.previewImpact` returns just the single target session
  id (`Set.of(affectedEntityId())`) – consistent with the apply path.
* `DisruptionServiceImpl.applyDisruption` calls `cancelSession`, which clears the
  session's timeslot (`sessionRepo.clearTimeslotForSession`) and returns a
  completed no-op. The session row is kept (it shows as an unplaced/orphan
  session, reversible by a later generate). Routing it through a partial resolve
  would be impossible because `timeslot` is a mandatory planning variable – the
  solver could never "leave it unplaced" and the job would always come back
  `INFEASIBLE`.

## 5. Service wiring — `DisruptionServiceImpl` (`impact/DisruptionServiceImpl.java`)

### `previewImpact` (read-only)

Validates schedule + request, loads sessions, and either returns the single
cancelled session (no BFS) or builds the graph + runs `ImpactAnalyzer`. It then
summarises impacted sessions (`DisruptionResponse.ImpactedSession`) with
subject, batch label, teacher, room, day, and times. Used to show the operator
the blast radius before committing.

### `applyDisruption` (writes)

* `SESSION_CANCELLED` → `cancelSession` (direct).
* Otherwise build graph, analyze, and if `impactedIds.isEmpty()` return a
  completed no-op. Else `solveJobService.ensureNoActiveJobForSchedule` then
  `submitPartialResolve(scheduleId, impactedIds, buildFacts(request))`.

### `buildFacts(request)` → `List<DisruptionConstraintFact>`

Converts the disruption into solver facts so the re-solve is *forced* to move the
affected sessions:

| Type | Fact produced |
|------|---------------|
| `TIMESLOT_BLOCKED` | `(TIMESLOT_BLOCKED, affectedEntityId, null)` |
| `SPECIAL_EVENT` | `(SPECIAL_EVENT, null, day)` – **only if a day is known** |
| `TEACHER_UNAVAILABLE` / `ROOM_UNAVAILABLE` | `(type, affectedEntityId, day)` – **only if day known and entity id known** |

A dayless teacher/room/special-event fact is suppressed because it would wrongly
force *every* session of that teacher/room off the timetable across all days.
These facts feed the `disruptionViolation` HARD constraint (see
`SCHEDULING_SOLVER.md` §6), guaranteeing the partial resolve genuinely changes
the timetable. The `impactedSessionIds` list is what the partial-resolve job
passes to `TimetableProblemBuilder`, which pins every *non*-impacted session and
unlocks the impacted ones (see `SCHEDULING_SOLVER.md` §4.1).
