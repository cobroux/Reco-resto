package hr.emocio.recoresto.search.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CriteriaExtractorTest {

    private final LlmChatClient llm = mock(LlmChatClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final CriteriaExtractor extractor = new CriteriaExtractor(llm, JsonMapper.builder().build(), clock);

    private void llmReplies(String reply) {
        when(llm.complete(anyString(), anyString())).thenReturn(reply);
    }

    @Test
    void asianOrVegetarianAroundOperaNow() {
        llmReplies("""
                {"understood":true,"cuisines":["asian"],"diets":["vegetarian"],
                 "combination":{"operator":"OR","children":[
                   {"kind":"cuisine","value":"asian"},{"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra, Paris"],"moment":"now","distanceLimitMeters":null}""");

        SearchCriteria criteria = extractor
                .extract("J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris");

        assertThat(criteria.cuisines()).containsExactly("asian");
        assertThat(criteria.diets()).containsExactly("vegetarian");
        assertThat(criteria.combination().operator()).isEqualTo("OR");
        assertThat(criteria.combination().children()).extracting(CriteriaNode::value)
                .containsExactly("asian", "vegetarian");
        assertThat(criteria.places()).containsExactly("Opéra, Paris");
        assertThat(criteria.moment()).isEqualTo("now");
        assertThat(criteria.distanceLimitMeters()).isNull();
    }

    @Test
    void asianAndVegetarianIsCombinedWithAnd() {
        llmReplies("""
                {"understood":true,"cuisines":["asian"],"diets":["vegetarian"],
                 "combination":{"operator":"AND","children":[
                   {"kind":"cuisine","value":"asian"},{"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra"],"moment":"now"}""");

        assertThat(extractor.extract("asiatique et végétarien près d'Opéra").combination().operator())
                .isEqualTo("AND");
    }

    @Test
    void mixedAndOrKeepsTheGroupingOfTheUser() {
        llmReplies("""
                {"understood":true,"cuisines":["asian","italian"],"diets":["vegetarian"],
                 "combination":{"operator":"AND","children":[
                   {"operator":"OR","children":[
                     {"kind":"cuisine","value":"asian"},{"kind":"cuisine","value":"italian"}]},
                   {"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra"],"moment":"now"}""");

        CriteriaNode root = extractor.extract("asiatique ou italien, et végétarien").combination();

        assertThat(root.operator()).isEqualTo("AND");
        assertThat(root.children().get(0).operator()).isEqualTo("OR");
        assertThat(root.children().get(0).children()).hasSize(2);
        assertThat(root.children().get(1).value()).isEqualTo("vegetarian");
    }

    @Test
    void severalPlacesAreKept() {
        llmReplies("""
                {"understood":true,"cuisines":[],"diets":[],"places":["Opéra","Châtelet"],"moment":"now"}""");

        assertThat(extractor.extract("entre Opéra et Châtelet").places()).containsExactly("Opéra", "Châtelet");
    }

    @Test
    void placesGivenAsObjectsWithANameAndACityAreAccepted() {
        llmReplies("""
                {"understood":true,"places":[{"name":"Opéra","city":"Paris"},{"name":"Châtelet"},"Montmartre"],
                 "moment":"now"}""");

        assertThat(extractor.extract("Opéra, Châtelet ou Montmartre").places())
                .containsExactly("Opéra, Paris", "Châtelet", "Montmartre");
    }

    @Test
    void distanceLimitIsKept() {
        llmReplies("""
                {"understood":true,"places":["Opéra"],"moment":"now","distanceLimitMeters":800}""");

        assertThat(extractor.extract("à moins de 10 minutes à pied d'Opéra").distanceLimitMeters())
                .isEqualTo(800);
    }

    @Test
    void budgetAndDishRepliedByTheModelAreNotKept() {
        llmReplies("""
                {"understood":true,"cuisines":["japanese"],"places":["Opéra"],"moment":"now",
                 "budget":"cheap","dish":"ramen"}""");

        SearchCriteria criteria = extractor.extract("un ramen pas cher près d'Opéra");

        assertThat(criteria.cuisines()).containsExactly("japanese");
        assertThat(criteria.toString()).doesNotContain("cheap").doesNotContain("ramen");
    }

    @Test
    void sentenceNotUnderstoodByTheModelGivesNotUnderstood() {
        llmReplies("{\"understood\":false}");

        assertThatThrownBy(() -> extractor.extract("blablabla")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }

    @Test
    void replyThatIsNotJsonGivesNotUnderstood() {
        llmReplies("Désolé, je ne sais pas.");

        assertThatThrownBy(() -> extractor.extract("blablabla")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }

    @Test
    void jsonWrappedInACodeFenceIsAccepted() {
        llmReplies("```json\n{\"understood\":true,\"places\":[\"Opéra\"],\"moment\":\"now\"}\n```");

        assertThat(extractor.extract("Opéra").places()).containsExactly("Opéra");
    }

    @Test
    void sendsTheSentenceAndTheCurrentParisDateTimeToTheModel() {
        llmReplies("{\"understood\":true,\"places\":[\"Opéra\"],\"moment\":\"now\"}");

        extractor.extract("samedi midi à Opéra");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), user.capture());
        assertThat(user.getValue()).isEqualTo("samedi midi à Opéra");
        assertThat(system.getValue()).contains("2026-11-24T11:00");
    }
}
