package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.restaurant.OpeningPeriod;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Converts Google {@code regularOpeningHours.periods} (regular weekly hours, D9 (2)) to the weekly hours of
 * {@code Restaurant.openingHours} (spec 008 data-model.md "Opening hours mapping", research R4), so that the filters
 * and the frontend stay unchanged.
 */
final class GoogleOpeningHours {

    private static final String MIDNIGHT = "00:00";

    private GoogleOpeningHours() {
    }

    /** The weekly hours, or null when Google gives none (no periods is treated as unknown hours). */
    static Map<String, List<OpeningPeriod>> parse(JsonNode regularOpeningHours) {
        JsonNode periods = regularOpeningHours.path("periods");
        if (!periods.isArray() || periods.isEmpty()) {
            return null;
        }
        Map<String, List<OpeningPeriod>> hours = new LinkedHashMap<>();
        for (JsonNode period : periods) {
            JsonNode open = period.path("open");
            JsonNode close = period.path("close");
            if (!open.path("day").isNumber()) {
                continue;
            }
            if (!close.path("day").isNumber()) {
                // Open without close: open 24 hours, every day (research R4).
                for (int day = 0; day < 7; day++) {
                    add(hours, day, MIDNIGHT, MIDNIGHT);
                }
                continue;
            }
            int openDay = open.path("day").asInt();
            int closeDay = close.path("day").asInt();
            if (!isDay(openDay) || !isDay(closeDay)) {
                continue;
            }
            String opens = time(open);
            String closes = time(close);
            if (openDay == closeDay) {
                add(hours, openDay, opens, closes);
                continue;
            }
            // Over midnight: until the end of the open day, whole days in between, then from midnight on the close
            // day (nothing on the close day when it closes exactly at midnight).
            add(hours, openDay, opens, MIDNIGHT);
            for (int day = (openDay + 1) % 7; day != closeDay; day = (day + 1) % 7) {
                add(hours, day, MIDNIGHT, MIDNIGHT);
            }
            if (!MIDNIGHT.equals(closes)) {
                add(hours, closeDay, MIDNIGHT, closes);
            }
        }
        return hours.isEmpty() ? null : hours;
    }

    private static void add(Map<String, List<OpeningPeriod>> hours, int googleDay, String opens, String closes) {
        hours.computeIfAbsent(dayName(googleDay), day -> new ArrayList<>()).add(new OpeningPeriod(opens, closes));
    }

    private static boolean isDay(int googleDay) {
        return googleDay >= 0 && googleDay <= 6;
    }

    /** Google day 0 = Sunday ... 6 = Saturday. */
    private static String dayName(int googleDay) {
        return DayOfWeek.of(googleDay == 0 ? 7 : googleDay).name();
    }

    private static String time(JsonNode point) {
        return "%02d:%02d".formatted(point.path("hour").asInt(0), point.path("minute").asInt(0));
    }
}
