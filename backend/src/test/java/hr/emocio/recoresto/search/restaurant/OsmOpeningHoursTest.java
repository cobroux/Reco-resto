package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The OpenStreetMap {@code opening_hours} tag: a safe subset is read, anything else is "unknown" (null). */
class OsmOpeningHoursTest {

    private static final List<String> WEEK = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
            "SATURDAY", "SUNDAY");

    private static OpeningPeriod period(String opens, String closes) {
        return new OpeningPeriod(opens, closes);
    }

    @Test
    void aRangeOfDaysWithOneTimeRange() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Mo-Su 12:00-23:00");

        assertThat(hours.keySet()).containsExactlyInAnyOrderElementsOf(WEEK);
        assertThat(hours.get("MONDAY")).containsExactly(period("12:00", "23:00"));
        assertThat(hours.get("SUNDAY")).containsExactly(period("12:00", "23:00"));
    }

    @Test
    void severalRulesAndSeveralTimeRangesInOneDay() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse(
                "Mo-Fr 12:00-14:30,19:00-23:00; Sa 19:00-23:00");

        assertThat(hours.get("WEDNESDAY")).containsExactly(period("12:00", "14:30"), period("19:00", "23:00"));
        assertThat(hours.get("SATURDAY")).containsExactly(period("19:00", "23:00"));
        assertThat(hours).doesNotContainKey("SUNDAY");
    }

    @Test
    void aListOfDays() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Mo,We,Fr 10:00-12:00");

        assertThat(hours.keySet()).containsExactlyInAnyOrder("MONDAY", "WEDNESDAY", "FRIDAY");
    }

    @Test
    void aRangeOfDaysAndASingleDayCanBeMixed() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Mo-Tu,Su 10:00-12:00");

        assertThat(hours.keySet()).containsExactlyInAnyOrder("MONDAY", "TUESDAY", "SUNDAY");
    }

    @Test
    void aRangeOfDaysMayWrapAroundTheEndOfTheWeek() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Fr-Mo 18:00-23:00");

        assertThat(hours.keySet()).containsExactlyInAnyOrder("FRIDAY", "SATURDAY", "SUNDAY", "MONDAY");
    }

    @Test
    void offClosesTheDaysOfTheRule() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Mo-Sa 11:00-22:00; Su off");

        assertThat(hours).doesNotContainKey("SUNDAY");
        assertThat(hours).containsKey("SATURDAY");
    }

    @Test
    void aLaterRuleOverridesTheEarlierOneForTheSameDays() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("Mo-Su 10:00-22:00; Tu off; Fr 12:00-15:00");

        assertThat(hours).doesNotContainKey("TUESDAY");
        assertThat(hours.get("FRIDAY")).containsExactly(period("12:00", "15:00"));
        assertThat(hours.get("MONDAY")).containsExactly(period("10:00", "22:00"));
    }

    @Test
    void withoutDaysTheRuleAppliesToEveryDay() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("12:00-14:00");

        assertThat(hours.keySet()).containsExactlyInAnyOrderElementsOf(WEEK);
    }

    @Test
    void twentyFourSevenIsOpenAllDay() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("24/7");

        assertThat(hours.keySet()).containsExactlyInAnyOrderElementsOf(WEEK);
        assertThat(hours.get("TUESDAY")).containsExactly(period("00:00", "23:59"));
    }

    @Test
    void aClosingTimeAfterMidnightIsKeptAsWritten() {
        assertThat(OsmOpeningHours.parse("Fr 18:00-02:00").get("FRIDAY")).containsExactly(period("18:00", "02:00"));
    }

    @Test
    void twentyFourOClockMeansTheEndOfTheDay() {
        assertThat(OsmOpeningHours.parse("Mo 12:00-24:00").get("MONDAY")).containsExactly(period("12:00", "23:59"));
    }

    @Test
    void spacesAroundTheSeparatorsAreTolerated() {
        Map<String, List<OpeningPeriod>> hours = OsmOpeningHours.parse("  Mo-Fr  12:00-14:00 , 19:00-23:00 ;  Sa off ");

        assertThat(hours.get("MONDAY")).containsExactly(period("12:00", "14:00"), period("19:00", "23:00"));
    }

    @Test
    void whatIsNotSupportedIsUnknownRatherThanGuessed() {
        for (String unsupported : List.of("Mo-Fr 12:00-14:00; PH off", "Mo-Su sunrise-sunset",
                "Jan-Mar Mo 10:00-12:00", "Mo-Fr 12:00-14:00 \"by appointment\"", "Mo-Fr 12:00+", "week 1-10 Mo 10:00-12:00",
                "garbage", "Mo-Fr 25:00-26:00", "Xx 10:00-12:00")) {
            assertThat(OsmOpeningHours.parse(unsupported)).as(unsupported).isNull();
        }
    }

    @Test
    void anEmptyOrMissingValueIsUnknown() {
        assertThat(OsmOpeningHours.parse(null)).isNull();
        assertThat(OsmOpeningHours.parse("  ")).isNull();
    }

    @Test
    void aValueThatClosesEveryDayIsUnknownBecauseNoOpeningIsGiven() {
        assertThat(OsmOpeningHours.parse("off")).isNull();
    }
}
