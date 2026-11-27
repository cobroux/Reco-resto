package hr.emocio.recoresto.hello;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Hello", description = "Sample endpoint to check that the API is up")
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Return a greeting")
    public HelloResponse hello() {
        return new HelloResponse("Hello from Reco-Resto");
    }

    public record HelloResponse(String message) {
    }
}
