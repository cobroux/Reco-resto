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

    @Test
    void validCoordinatesArePointsOnEarth() {
        assertThat(Position.isValid(48.87, 2.33)).isTrue();
        assertThat(Position.isValid(90, 180)).isTrue();
        assertThat(Position.isValid(-90, -180)).isTrue();
        assertThat(Position.isValid(148.87, 2.33)).isFalse();
        assertThat(Position.isValid(48.87, -180.5)).isFalse();
    }

    /** Text Search accepts a rectangle only: the smallest one holding the search circle. */
    @Test
    void theBoundingBoxHoldsTheCircleAndTouchesIt() {
        Position opera = new Position(48.8708, 2.3322);

        Position.Box box = opera.boundingBox(2500);

        assertThat(box.low().latitude()).isLessThan(opera.latitude());
        assertThat(box.low().longitude()).isLessThan(opera.longitude());
        assertThat(opera.distanceMetersTo(new Position(box.high().latitude(), opera.longitude())))
                .isCloseTo(2500.0, within(0.5));
        assertThat(opera.distanceMetersTo(new Position(box.low().latitude(), opera.longitude())))
                .isCloseTo(2500.0, within(0.5));
        // the farthest east point of the circle is on the east side of the box
        assertThat(opera.distanceMetersTo(new Position(opera.latitude(), box.high().longitude())))
                .isGreaterThanOrEqualTo(2500.0).isCloseTo(2500.0, within(5.0));
    }

    @Test
    void theBoundingBoxStaysOnEarth() {
        Position.Box box = new Position(89.99, 179.99).boundingBox(5000);

        assertThat(box.high().latitude()).isEqualTo(90.0);
        assertThat(box.high().longitude()).isEqualTo(180.0);
        assertThat(box.low().longitude()).isEqualTo(-180.0);
    }
}
