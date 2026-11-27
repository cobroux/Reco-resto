package hr.emocio.recoresto.search.tool;

import hr.emocio.recoresto.search.api.Position;

/**
 * What the tools of one search share (data-model.md "ToolContext"). It lives for one request only, so there is no
 * state shared between requests.
 */
public final class ToolContext {

    private final Position position;
    private ToolRun lastRun;

    public ToolContext(Position position) {
        this.position = position;
    }

    /** The user position of the request, never null. */
    public Position position() {
        return position;
    }

    /** The last run that found candidates, or the first run when none did; null before any search. */
    public ToolRun lastRun() {
        return lastRun;
    }

    /** A new search replaces the previous one only when it found something (never lose results). */
    public void record(ToolRun run) {
        if (lastRun == null || !run.candidates().isEmpty()) {
            lastRun = run;
        }
    }
}
