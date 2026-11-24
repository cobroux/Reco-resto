package hr.emocio.recoresto.search.api;

/** The user's sentence and, when it is used, the device position. */
public record SearchRequest(String sentence, Position position) {

    public SearchRequest {
        if (sentence == null || sentence.isBlank()) {
            throw new IllegalArgumentException("sentence must not be blank");
        }
    }
}
