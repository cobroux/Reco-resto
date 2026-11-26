package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.List;

/**
 * What the language model understood from a sentence (spec 008 data-model.md "Model answer"): its state, the criteria
 * and the places it named. The model never gives restaurants: the backend finds them.
 */
public record SearchAnswer(State state, SearchCriteria criteria, List<NamedPlace> places) {

    public enum State {
        /** A restaurant request: the criteria and places are set. */
        UNDERSTOOD,
        /** The model states the sentence is not a restaurant request. */
        NOT_A_REQUEST,
        /** The model states a place of the sentence is outside France. */
        OUTSIDE_FRANCE,
        /** No answer could be read (no JSON object, invalid JSON, empty): never "not a request" (FR-007). */
        UNREADABLE
    }

    public SearchAnswer {
        places = places == null ? List.of() : List.copyOf(places);
    }

    public static SearchAnswer notARequest() {
        return new SearchAnswer(State.NOT_A_REQUEST, null, List.of());
    }

    public static SearchAnswer unreadable() {
        return new SearchAnswer(State.UNREADABLE, null, List.of());
    }

    public static SearchAnswer placeOutsideFrance() {
        return new SearchAnswer(State.OUTSIDE_FRANCE, null, List.of());
    }
}
