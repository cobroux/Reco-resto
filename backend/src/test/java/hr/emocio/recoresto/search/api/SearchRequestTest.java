package hr.emocio.recoresto.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SearchRequestTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankOrMissingSentenceIsRejected(String sentence) {
        assertThatThrownBy(() -> new SearchRequest(sentence, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionIsOptional() {
        assertThat(new SearchRequest("thaï ce soir", null).position()).isNull();
    }

    @Test
    void keepsSentenceAndPosition() {
        SearchRequest request = new SearchRequest("thaï", new Position(48.87, 2.33));
        assertThat(request.sentence()).isEqualTo("thaï");
        assertThat(request.position()).isEqualTo(new Position(48.87, 2.33));
    }
}
