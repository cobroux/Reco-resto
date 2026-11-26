package hr.emocio.recoresto.search.api;

import org.springframework.http.HttpStatus;

public enum SearchErrorCode {
    NOT_UNDERSTOOD(HttpStatus.UNPROCESSABLE_CONTENT),
    NO_RESULT(HttpStatus.UNPROCESSABLE_CONTENT),
    PLACE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    SearchErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
