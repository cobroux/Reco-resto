package hr.emocio.recoresto.search.restaurant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the OpenStreetMap {@code opening_hours} tag into weekly hours keyed by upper-case English day name.
 *
 * <p>Only a safe subset is read: rules separated by {@code ;}, each with optional days ({@code Mo}, {@code Mo-Fr},
 * {@code Mo,We}, a range may wrap such as {@code Fr-Mo}) then {@code off} or time ranges ({@code 12:00-14:30,19:00-23:00},
 * {@code 24:00} meaning the end of the day), and {@code 24/7}. A later rule replaces the earlier one for the same days.
 * Anything else (public holidays, months, weeks, sunrise, comments, open end) makes the whole value unknown: the
 * result is null rather than a guess.
 */
final class OsmOpeningHours {

    private static final List<String> OSM_DAYS = List.of("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");
    private static final List<String> DAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
            "SATURDAY", "SUNDAY");
    private static final Pattern TIME_RANGE = Pattern.compile("(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})");
    private static final String END_OF_DAY = "23:59";

    private OsmOpeningHours() {
    }

    /** The weekly hours, or null when the value is missing, not supported, or closes every day. */
    static Map<String, List<OpeningPeriod>> parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Map<String, List<OpeningPeriod>> week = new LinkedHashMap<>();
        for (String rule : value.trim().replaceAll("\\s*,\\s*", ",").split(";")) {
            String trimmed = rule.trim();
            if (!trimmed.isEmpty() && !apply(trimmed, week)) {
                return null;
            }
        }
        return week.isEmpty() ? null : week;
    }

    private static boolean apply(String rule, Map<String, List<OpeningPeriod>> week) {
        if (rule.equals("24/7")) {
            DAYS.forEach(day -> week.put(day, List.of(new OpeningPeriod("00:00", END_OF_DAY))));
            return true;
        }
        String[] parts = rule.split("\\s+");
        List<String> days = DAYS;
        int next = 0;
        if (Character.isLetter(parts[0].charAt(0)) && !parts[0].equals("off")) {
            days = days(parts[0]);
            if (days == null) {
                return false;
            }
            next = 1;
        }
        String rest = String.join(" ", Arrays.copyOfRange(parts, next, parts.length));
        if (rest.equals("off")) {
            days.forEach(week::remove);
            return true;
        }
        List<OpeningPeriod> periods = periods(rest);
        if (periods == null) {
            return false;
        }
        days.forEach(day -> week.put(day, periods));
        return true;
    }

    /** {@code Mo}, {@code Mo-Fr}, a comma separated list of them; null when something else is written. */
    private static List<String> days(String selector) {
        List<String> days = new ArrayList<>();
        for (String item : selector.split(",", -1)) {
            String[] bounds = item.split("-", -1);
            if (bounds.length > 2) {
                return null;
            }
            int from = OSM_DAYS.indexOf(bounds[0]);
            int to = bounds.length == 2 ? OSM_DAYS.indexOf(bounds[1]) : from;
            if (from < 0 || to < 0) {
                return null;
            }
            for (int day = from;; day = (day + 1) % 7) {
                days.add(DAYS.get(day));
                if (day == to) {
                    break;
                }
            }
        }
        return days;
    }

    /** {@code 12:00-14:30,19:00-23:00}; null when something else is written. */
    private static List<OpeningPeriod> periods(String times) {
        if (times.isEmpty()) {
            return null;
        }
        List<OpeningPeriod> periods = new ArrayList<>();
        for (String range : times.split(",", -1)) {
            Matcher match = TIME_RANGE.matcher(range);
            if (!match.matches()) {
                return null;
            }
            String opens = time(match.group(1), match.group(2), false);
            String closes = time(match.group(3), match.group(4), true);
            if (opens == null || closes == null) {
                return null;
            }
            periods.add(new OpeningPeriod(opens, closes));
        }
        return periods;
    }

    private static String time(String hours, String minutes, boolean mayBeEndOfDay) {
        int h = Integer.parseInt(hours);
        int m = Integer.parseInt(minutes);
        if (m > 59 || h > 24 || (h == 24 && (m != 0 || !mayBeEndOfDay))) {
            return null;
        }
        return h == 24 ? END_OF_DAY : hours + ":" + minutes;
    }
}
