package hr.emocio.recoresto.search.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/throw/{code}")
        void fail(@PathVariable SearchErrorCode code) {
            throw new SearchException(code, "message for " + code);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @ParameterizedTest
    @CsvSource({
            "NOT_UNDERSTOOD,422",
            "NO_RESULT,422",
            "OUTSIDE_FRANCE,422",
            "PLACE_REQUIRED,422",
            "SERVICE_UNAVAILABLE,503"
    })
    void mapsEachErrorCodeToItsStatusWithCodeAndMessage(String code, int status) throws Exception {
        mockMvc.perform(get("/throw/" + code))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("message for " + code));
    }

    @Test
    void errorCodesMatchTheContract() {
        org.assertj.core.api.Assertions.assertThat(SearchErrorCode.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("NOT_UNDERSTOOD", "NO_RESULT", "OUTSIDE_FRANCE", "PLACE_REQUIRED",
                        "SERVICE_UNAVAILABLE");
    }
}
