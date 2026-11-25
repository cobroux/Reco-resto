package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One opening period of a day, as local "HH:mm" times. A closing time not after the opening time means "until the end of the day". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpeningPeriod(String opens, String closes) {
}
