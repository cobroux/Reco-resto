package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchAnswerParser;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asks the language model, in one call and without tools, what it understood from the sentence (spec 008 FR-002,
 * research R8). The model only gives criteria and places; the backend builds the restaurant search itself. Cuisines
 * come from the closed list of {@link GooglePlaceTypes} (D7).
 */
@Component
public class SentenceInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SentenceInterpreter.class);

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final String INSTRUCTIONS = """
            You help to find restaurants in France from a sentence written in French.
            Current date and time: %s (time zone %s).
            Understand the sentence and reply with what you understood; the backend finds the restaurants itself.
            Give the coordinates of every place the sentence names when you know them.
            Cuisine values: use only these Google place types, written exactly as here: %s.
            For a broad cuisine (for example asiatique), put every type of this list that is part of it.
            Diet values: English identifiers such as vegetarian, vegan, halal.
            Reply with one JSON object only, no other text:
            {"understood":true|false,"outsideFrance":true|false,
             "criteria":{"cuisines":[],"diets":[],
               "combination":{"operator":"AND|OR","kind":"cuisine|diet","value":"...","children":[...]},
               "places":[],"moment":"now or a local date-time such as 2026-11-28T12:00","distanceLimitMeters":null,
               "walkingMinutes":null},
             "centers":[{"name":"...","latitude":0.0,"longitude":0.0}]}
            Rules:
            - "understood" is false when the sentence is not a restaurant request.
            - "outsideFrance" is true when a place named is outside France.
            - Keep the AND/OR grouping expressed by the user in "combination".
            - A budget or a specific dish is not a criterion: ignore it.
            - "moment" is "now" when the sentence gives none. "distanceLimitMeters" only when the sentence gives a
              distance.
            - Every travel time is always on foot, whatever the mode named: when the sentence gives a time to get there,
              put its minutes in "walkingMinutes".
            - One center per place named, with its name; leave out the coordinates of a place you do not know.
            - Never write null or an empty value: leave the field out.""";

    private static final String UNREADABLE_REPAIR = """
            Sentence: %s
            Your previous answer could not be read: %s
            Reply with the JSON object only, as described, no other text.""";

    private static final String CUISINE_REPAIR = """
            Sentence: %s
            Your previous answer used cuisine values outside the list: %s.
            Previous answer: %s
            Use only the cuisine values of the list. Reply with the JSON object only, as described, no other text.""";

    private final LlmChatClient llm;
    private final SearchAnswerParser parser;
    private final int repairs;
    private final Clock clock;

    public SentenceInterpreter(LlmChatClient llm, SearchAnswerParser parser, LlmProperties llmProperties,
            Clock clock) {
        this.llm = llm;
        this.parser = parser;
        this.repairs = llmProperties.emptyAnswerRetries() == null ? 0 : Math.max(0, llmProperties.emptyAnswerRetries());
        this.clock = clock;
    }

    /**
     * What the model understood. An unusable answer (unreadable, or a cuisine outside the closed list) is sent back to
     * the model, which is asked for the JSON only (D11, decision Q3), up to {@code llm.empty-answer-retries} times for
     * the sentence. Still unreadable: service unavailable (FR-007); still a cuisine outside the list: no result, the
     * search is never widened (Q3).
     */
    public SearchAnswer interpret(String sentence) {
        String system = systemPrompt();
        String message = sentence;
        for (int attempt = 0;; attempt++) {
            String reply = llm.complete(system, message);
            SearchAnswer answer = parser.parse(reply);
            List<String> unknown = answer.state() == SearchAnswer.State.UNDERSTOOD
                    ? CriteriaMatcher.cuisineValues(answer.criteria()).stream()
                            .filter(value -> !GooglePlaceTypes.isCuisine(value)).toList()
                    : List.of();
            boolean unreadable = answer.state() == SearchAnswer.State.UNREADABLE;
            if (!unreadable && unknown.isEmpty()) {
                return answer;
            }
            String problem = unreadable ? "unreadable" : "cuisine outside the closed list: " + unknown;
            if (attempt >= repairs) {
                log.warn("Unusable model answer ({}) after {} repair(s)", problem, repairs);
                throw unreadable
                        ? new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The model answer cannot be read")
                        : new SearchException(SearchErrorCode.NO_RESULT, "A cuisine is outside the closed list");
            }
            log.info("Unusable model answer ({}): repair {} of {}", problem, attempt + 1, repairs);
            message = unreadable ? UNREADABLE_REPAIR.formatted(sentence, reply)
                    : CUISINE_REPAIR.formatted(sentence, String.join(", ", unknown), reply);
        }
    }

    String systemPrompt() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(MomentResolver.ZONE));
        return INSTRUCTIONS.formatted(now.format(NOW_FORMAT), MomentResolver.ZONE,
                String.join(", ", GooglePlaceTypes.CUISINE_TYPES));
    }
}
