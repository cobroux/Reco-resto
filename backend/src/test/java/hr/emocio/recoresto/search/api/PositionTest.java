package hr.emocio.recoresto.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void distanceBetweenParisAndLyonIsAbout392Km() {
        Position paris = new Position(48.8566, 2.3522);
        Position lyon = new Position(45.7640, 4.8357);
        assertThat(paris.distanceMetersTo(lyon)).isCloseTo(392_000.0, within(3_000.0));
    }

    @Test
    void distanceToItselfIsZero() {
        Position paris = new Position(48.8566, 2.3522);
        assertThat(paris.distanceMetersTo(paris)).isZero();
    }
}
