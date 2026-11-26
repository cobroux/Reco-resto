package hr.emocio.recoresto.search.enrichment;

/** A website page that could not be read, with the outcome it gives (data-model.md "Enrichment outcome"). */
public class FetchException extends Exception {

    private final EnrichmentOutcome outcome;

    public FetchException(EnrichmentOutcome outcome, String message) {
        super(message, null, false, false);
        this.outcome = outcome;
    }

    public EnrichmentOutcome outcome() {
        return outcome;
    }
}
