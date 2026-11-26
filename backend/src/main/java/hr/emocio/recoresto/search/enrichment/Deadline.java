package hr.emocio.recoresto.search.enrichment;

import java.time.Duration;

/** The end of the budget of one enrichment (spec 007 FR-011, research R6), on the monotonic clock. */
public final class Deadline {

    private final long endNanos;

    private Deadline(long endNanos) {
        this.endNanos = endNanos;
    }

    /** A deadline the given time from now. */
    public static Deadline after(Duration budget) {
        return new Deadline(System.nanoTime() + budget.toNanos());
    }

    /** What is left of the budget; zero or negative once it is spent. */
    public Duration remaining() {
        return Duration.ofNanos(endNanos - System.nanoTime());
    }

    public boolean isSpent() {
        return remaining().compareTo(Duration.ZERO) <= 0;
    }
}
