package hr.emocio.recoresto.search.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MomentResolverTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final MomentResolver resolver = new MomentResolver(clock);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "now", "NOW"})
    void noMomentMeansNow(String moment) {
        assertThat(resolver.resolve(moment)).isEqualTo(ZonedDateTime.of(2026, 11, 24, 11, 0, 0, 0, PARIS));
    }

    @Test
    void localDateTimeIsReadInTheParisTimeZone() {
        assertThat(resolver.resolve("2026-11-28T12:00"))
                .isEqualTo(ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, PARIS));
    }

    @Test
    void dateTimeWithOffsetIsKeptAtTheSameInstant() {
        assertThat(resolver.resolve("2026-11-28T12:00:00+01:00").toInstant())
                .isEqualTo(Instant.parse("2026-11-28T11:00:00Z"));
    }

    @Test
    void aMomentOnAnotherComingDayIsAccepted() {
        assertThat(resolver.resolve("2026-12-05T20:00").getDayOfMonth()).isEqualTo(5);
    }

    @Test
    void aMomentInThePastIsNotUnderstood() {
        assertThatThrownBy(() -> resolver.resolve("2026-11-23T20:00")).isInstanceOfSatisfying(
                SearchException.class, e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }

    @Test
    void anUnreadableMomentIsNotUnderstood() {
        assertThatThrownBy(() -> resolver.resolve("ce soir")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }
}
