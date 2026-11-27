package hr.emocio.recoresto.search.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Spec 007 research R7 (DRY): the first JSON object of a model answer, shared by every answer reader. */
class JsonAnswersTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsTheObjectWhateverProseOrCodeFenceSurroundsIt() {
        assertThat(JsonAnswers.firstObject(mapper, "Here it is:\n```json\n{\"choice\": 2}\n```\nThanks"))
                .get().satisfies(node -> assertThat(node.path("choice").asInt()).isEqualTo(2));
    }

    @Test
    void twoObjectsGiveTheFirst() {
        assertThat(JsonAnswers.firstObject(mapper, "{\"a\":1} {\"a\":2}")).get()
                .satisfies(node -> assertThat(node.path("a").asInt()).isEqualTo(1));
    }

    @Test
    void bracesInsideStringsAreNotStructure() {
        assertThat(JsonAnswers.firstObject(mapper, "{\"text\":\"a } b { \\\" c\",\"n\":3}")).get()
                .satisfies(node -> {
                    assertThat(node.path("text").asString()).isEqualTo("a } b { \" c");
                    assertThat(node.path("n").asInt()).isEqualTo(3);
                });
    }

    @Test
    void aBlockThatIsNotJsonIsSkipped() {
        assertThat(JsonAnswers.firstObject(mapper, "{not json} then {\"ok\":true}")).get()
                .satisfies(node -> assertThat(node.path("ok").asBoolean()).isTrue());
    }

    @Test
    void textIsTheTrimmedNonBlankString() {
        assertThat(JsonAnswers.text(mapper.readTree("\"  pizza \""))).isEqualTo("pizza");
    }

    @Test
    void textIsNullWhenBlankMissingOrNotAString() {
        assertThat(JsonAnswers.text(mapper.readTree("\"   \""))).isNull();
        assertThat(JsonAnswers.text(mapper.readTree("12"))).isNull();
        assertThat(JsonAnswers.text(mapper.readTree("{}").path("absent"))).isNull();
    }

    @Test
    void noObjectGivesEmpty() {
        assertThat(JsonAnswers.firstObject(mapper, "no object here")).isEmpty();
        assertThat(JsonAnswers.firstObject(mapper, "[1, 2]")).isEmpty();
        assertThat(JsonAnswers.firstObject(mapper, "{\"open\": ")).isEmpty();
        assertThat(JsonAnswers.firstObject(mapper, null)).isEmpty();
    }
}
