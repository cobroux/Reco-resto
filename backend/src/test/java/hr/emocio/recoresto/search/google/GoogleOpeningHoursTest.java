package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.restaurant.OpeningPeriod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T23, data-model.md "Opening hours mapping". */
class GoogleOpeningHoursTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private Map<String, List<OpeningPeriod>> parse(String json) {
        JsonNode node = json == null ? mapper.missingNode() : mapper.readTree(json);
        return GoogleOpeningHours.parse(node);
    }

    private static String point(int day, int hour, int minute) {
        return "{\"day\":" + day + ",\"hour\":" + hour + ",\"minute\":" + minute + "}";
    }

    private static String period(String open, String close) {
        return close == null ? "{\"open\":" + open + "}" : "{\"open\":" + open + ",\"close\":" + close + "}";
    }

    private static String hours(String... periods) {
        return "{\"periods\":[" + String.join(",", periods) + "]}";
    }

    @Test
    void googleDaysZeroToSixAreSundayToSaturday() {
        String[] expected = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        for (int day = 0; day < 7; day++) {
            Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(day, 12, 0), point(day, 14, 0))));

            assertThat(parsed).containsOnlyKeys(expected[day]);
        }
    }

    @Test
    void aPeriodWithinADayGivesOnePeriodWithTwoDigitTimes() {
        Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(1, 9, 5), point(1, 14, 30)),
                period(point(1, 19, 0), point(1, 22, 45))));

        assertThat(parsed.get("MONDAY")).containsExactly(new OpeningPeriod("09:05", "14:30"),
                new OpeningPeriod("19:00", "22:45"));
    }

    @Test
    void aPeriodOverMidnightIsSplitOnTheTwoDays() {
        Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(5, 18, 0), point(6, 2, 0))));

        assertThat(parsed.get("FRIDAY")).containsExactly(new OpeningPeriod("18:00", "00:00"));
        assertThat(parsed.get("SATURDAY")).containsExactly(new OpeningPeriod("00:00", "02:00"));
    }

    @Test
    void aPeriodOverMidnightFromSaturdayEndsOnSunday() {
        Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(6, 19, 0), point(0, 1, 30))));

        assertThat(parsed.get("SATURDAY")).containsExactly(new OpeningPeriod("19:00", "00:00"));
        assertThat(parsed.get("SUNDAY")).containsExactly(new OpeningPeriod("00:00", "01:30"));
    }

    /** Closing exactly at midnight is the end of the open day, not a whole next day. */
    @Test
    void aPeriodClosingAtMidnightStaysOnTheOpenDay() {
        Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(2, 19, 0), point(3, 0, 0))));

        assertThat(parsed).containsOnlyKeys("TUESDAY");
        assertThat(parsed.get("TUESDAY")).containsExactly(new OpeningPeriod("19:00", "00:00"));
    }

    @Test
    void openWithoutCloseIsOpenAllDayEveryDay() {
        Map<String, List<OpeningPeriod>> parsed = parse(hours(period(point(0, 0, 0), null)));

        assertThat(parsed).hasSize(7);
        assertThat(parsed.values()).allSatisfy(periods -> assertThat(periods)
                .containsExactly(new OpeningPeriod("00:00", "00:00")));
    }

    /** Google "never open" cannot be told from unknown by the existing model: treated as unknown. */
    @Test
    void emptyPeriodsAreUnknownHours() {
        assertThat(parse(hours())).isNull();
    }

    @Test
    void missingHoursAreUnknown() {
        assertThat(parse(null)).isNull();
        assertThat(parse("{}")).isNull();
    }
}
