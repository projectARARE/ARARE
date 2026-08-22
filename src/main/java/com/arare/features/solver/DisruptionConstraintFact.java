package com.arare.features.solver;

import com.arare.features.impact.DisruptionType;
import java.util.ArrayList;
import java.util.List;

/**
 * A solver-readable snapshot of a disruption being repaired by a partial
 * resolve. Without these facts the re-solve had no reason to move the impacted
 * sessions — the solver kept them exactly where they were and reported success,
 * so "apply disruption" appeared to do nothing.
 *
 * <p>Each fact encodes one constraint the solver must obey while re-solving:
 * <ul>
 *   <li>{@code TEACHER_UNAVAILABLE} / {@code ROOM_UNAVAILABLE} — sessions of the
 *       affected entity may not be scheduled on {@code day} (null = all days)</li>
 *   <li>{@code TIMESLOT_BLOCKED} — no session may be assigned to the affected slot</li>
 *   <li>{@code SESSION_CANCELLED} — the affected session must not be placed</li>
 *   <li>{@code SPECIAL_EVENT} — no session may be scheduled on {@code day}</li>
 * </ul>
 * </p>
 */
public record DisruptionConstraintFact(
    DisruptionType type,
    Long affectedEntityId,
    String day
) {

    public static List<DisruptionConstraintFact> empty() {
        return List.of();
    }

    /**
     * Encodes facts into the compact "TYPE:id:day;TYPE:id:day" form stored on a
     * solve job row so the async worker can rebuild the exact disruption set.
     */
    public static String encode(List<DisruptionConstraintFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (DisruptionConstraintFact f : facts) {
            if (sb.length() > 0) sb.append(';');
            sb.append(f.type()).append(':')
              .append(f.affectedEntityId() != null ? f.affectedEntityId() : "")
              .append(':')
              .append(f.day() != null ? f.day() : "");
        }
        return sb.toString();
    }

    public static List<DisruptionConstraintFact> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<DisruptionConstraintFact> facts = new ArrayList<>();
        for (String part : encoded.split(";")) {
            String[] tokens = part.split(":", -1);
            if (tokens.length < 3) continue;
            try {
                DisruptionType type = DisruptionType.valueOf(tokens[0]);
                Long id = tokens[1].isEmpty() ? null : Long.parseLong(tokens[1]);
                String day = tokens[2].isEmpty() ? null : tokens[2];
                facts.add(new DisruptionConstraintFact(type, id, day));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed tokens from a persisted snapshot; never
                // produced by the submit path.
            }
        }
        return facts;
    }
}