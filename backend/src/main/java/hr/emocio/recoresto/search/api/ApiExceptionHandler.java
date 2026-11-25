package hr.emocio.recoresto.search.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String code, String message) {
    }

    @ExceptionHandler(SearchException.class)
    ResponseEntity<ErrorBody> handle(SearchException exception) {
        return ResponseEntity.status(exception.code().status())
                .body(new ErrorBody(exception.code().name(), exception.getMessage()));
    }
}
