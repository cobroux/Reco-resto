package hr.emocio.recoresto.search.api;

public class SearchException extends RuntimeException {

    private final SearchErrorCode code;

    public SearchException(SearchErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SearchException(SearchErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public SearchErrorCode code() {
        return code;
    }
}
