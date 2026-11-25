package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Turns the moment of the criteria into a concrete date-time. */
@Component
public class MomentResolver {

    /** Time zone of the service (inferred from "France only"; to confirm with the team). */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final Clock clock;

    public MomentResolver(Clock clock) {
        this.clock = clock;
    }

    /** No moment or "now" means now; a moment in the past or an unreadable one is not understood. */
    public ZonedDateTime resolve(String moment) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZONE));
        if (moment == null || moment.isBlank() || moment.equalsIgnoreCase("now")) {
            return now;
        }
        ZonedDateTime resolved = parse(moment.trim());
        if (resolved.isBefore(now)) {
            throw new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The moment is in the past");
        }
        return resolved;
    }

    private static ZonedDateTime parse(String moment) {
        try {
            return LocalDateTime.parse(moment).atZone(ZONE);
        } catch (DateTimeParseException notLocal) {
            try {
                return OffsetDateTime.parse(moment).atZoneSameInstant(ZONE);
            } catch (DateTimeParseException notOffset) {
                throw new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The moment cannot be read");
            }
        }
    }
}
