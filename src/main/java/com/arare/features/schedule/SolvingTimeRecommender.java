package com.arare.features.schedule;

/**
 * Derives the recommended solve wall-clock directly from the problem scale.
 *
 * <p>The construction heuristic is the binding phase: it initializes one
 * session per step, and each step ranks every (teacher x room x timeslot)
 * value for that session. Regression runs on the reference dataset measured
 * roughly {@value #CH_CANDIDATES_PER_SECOND} such candidate evaluations per
 * second, so the seconds a solve needs just to initialize everything is about
 *
 * <pre>
 *   sessions x teachers x rooms x classTimeslots / 8000
 * </pre>
 *
 * The result is inflated by {@link #MARGIN_FACTOR} so local search has time to
 * polish the medium/soft penalty after initialization, then clamped to a sane
 * engineering range. The frontend pre-fills this value for the request instead
 * of the flat 30s application default, which only fits toy-scale problems.
 */
public final class SolvingTimeRecommender {

    /** Measured construction-heuristic candidate evaluations per second. */
    static final double CH_CANDIDATES_PER_SECOND = 8_000.0;

    /**
     * Multiplier over the raw CH estimate to leave room for local search.
     *
     * <p>Calibrated empirically on the reference dataset. {@code *2} was too
     * tight for the small-scale solve (208 sessions x 20 teachers x 12 rooms):
     * it still ended at -1 hard after 480s because local search was stuck on a
     * hard-penalty plateau. {@code *2.5} (~500s) reaches 0 hard and a fully
     * dense timetable on the same scope, which matched the regression runs, so
     * a full 4x (~1050s) buys little extra for the wall-clock it costs. Local
     * search is stochastic, so the margin should always be ≥ 2 to let it
     * escape plateaus rather than stopping right after initialization.
     */
    static final double MARGIN_FACTOR = 2.5;

    static final int MIN_SECONDS = 30;
    /**
     * Hard ceiling for a single solve: 5 hours. Long enough for very large
     * datasets even with the 2.5x local-search margin; larger values have no
     * practical upside (local search converges to a plateau that an extra
     * hour of wall-clock barely improves).
     */
    static final int MAX_SECONDS = 18_000;

    private SolvingTimeRecommender() {
    }

    /**
     * @param sessions      number of ClassSessions the solve will create
     * @param teachers      number of teachers in the solve scope
     * @param rooms         number of rooms in the solve scope
     * @param classTimeslots number of CLASS-type timeslots configured
     * @return recommended solving time in seconds (min 30, max 18000)
     */
    public static int recommend(int sessions, int teachers, int rooms, int classTimeslots) {
        long candidates = (long) Math.max(1, sessions)
                * Math.max(1, teachers)
                * Math.max(1, rooms)
                * Math.max(1, classTimeslots);
        double chSeconds = candidates / CH_CANDIDATES_PER_SECOND;
        int seconds = (int) Math.ceil(chSeconds * MARGIN_FACTOR);
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }
}