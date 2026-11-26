package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchAnswerParser;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asks the language model, in one call and without tools, what it understood from the sentence (spec 008 FR-002,
 * research R8). The model only gives criteria and places; the backend builds the restaurant search itself. What to eat
 * is a short French {@code query} that the backend sends to Google Text Search (experiment of 2026-11-26).
 */
@Component
public class SentenceInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SentenceInterpreter.class);

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final String INSTRUCTIONS = """
            You help to find restaurants in France from a sentence written in French.
            Current date and time: %s (time zone %s).
            Reply with one JSON object only, no other text:
            {"understood":true|false,"outsideFrance":true|false,
             "criteria":{"query":"...","diets":[],"places":[],
               "moment":"now or a local date-time such as 2026-11-28T12:00","distanceLimitMeters":null,
               "walkingMinutes":null},
             "centers":[{"name":"...","latitude":0.0,"longitude":0.0}]}
            Rules:
            - "understood" is false only when the sentence is not about finding somewhere to eat. A sentence about
              eating, even with no cuisine or with only a budget or a dish, is a request: "understood" is true.
            - "outsideFrance" is true when a place named is outside France.
            - "query": a short French search text of what to eat, with the words of the user (dish, cuisine, style,
              level, budget), for example "frites", "aligot", "restaurant étoilé Michelin", "pho",
              "asiatique ou végétarien"; "restaurant" when the sentence says nothing about what to eat. Never a place,
              a moment or a distance.
            - "diets": only the diets the user requires, in English (vegetarian, vegan, halal or another real diet);
              a taste, a price, takeaway or an alternative such as "asiatique ou végétarien" is never a diet.
            - When the sentence names a moment (for example ce midi, ce soir, samedi midi), "moment" is the local
              date-time of that moment, never "now"; "now" when the sentence gives none.
            - "distanceLimitMeters" only when the sentence gives a distance. Every travel time is on foot, whatever
              the mode named: put its minutes in "walkingMinutes".
            - One center per place named, with its name and its coordinates when you know them.
            - A cuisine, dish or nationality word (for example italien, chinois, asiatique, frites) is never a place:
              no center when the sentence names no place.
            - Never write null or an empty value: leave the field out.""";

    private static final String UNREADABLE_REPAIR = """
            Sentence: %s
            Your previous answer could not be read: %s
            Reply with the JSON object only, as described, no other text.""";

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
     * What the model understood. An unreadable answer is sent back to the model, which is asked for the JSON only
     * (D11), up to {@code llm.empty-answer-retries} times for the sentence; still unreadable: service unavailable
     * (FR-007).
     */
    public SearchAnswer interpret(String sentence) {
        String system = systemPrompt();
        String message = sentence;
        for (int attempt = 0;; attempt++) {
            String reply = llm.complete(system, message);
            SearchAnswer answer = parser.parse(reply);
            if (answer.state() != SearchAnswer.State.UNREADABLE) {
                return answer;
            }
            if (attempt >= repairs) {
                log.warn("Unreadable model answer after {} repair(s)", repairs);
                throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The model answer cannot be read");
            }
            log.info("Unreadable model answer: repair {} of {}", attempt + 1, repairs);
            message = UNREADABLE_REPAIR.formatted(sentence, reply);
        }
    }

    String systemPrompt() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(MomentResolver.ZONE));
        return INSTRUCTIONS.formatted(now.format(NOW_FORMAT), MomentResolver.ZONE);
    }
}
