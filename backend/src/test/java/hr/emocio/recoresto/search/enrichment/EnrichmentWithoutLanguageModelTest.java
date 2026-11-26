package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.api.CardEnrichmentController;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Spec 007 T27, FR-004, amendment (a): enrichment works with the language model unreachable and sends it no request,
 * because no enrichment class can reach it: no field nor constructor parameter of any class of the enrichment package
 * (nor of {@link CardEnrichmentController}) has a type of the {@code search.llm} package. The flows themselves run
 * without any model in {@link CardEnrichmentServiceTest}.
 */
class EnrichmentWithoutLanguageModelTest {

    private static final String LLM_PACKAGE = "hr.emocio.recoresto.search.llm.";

    private static List<Class<?>> enrichmentClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("hr.emocio.recoresto.search.enrichment")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (!type.getSimpleName().endsWith("Test")) {
                classes.add(type);
            }
        }
        classes.add(CardEnrichmentController.class);
        return classes;
    }

    @Test
    void noEnrichmentClassDependsOnTheLanguageModel() throws Exception {
        List<Class<?>> classes = enrichmentClasses();
        assertThat(classes).contains(CardEnrichmentService.class, LargestImageRule.class, SafeWebFetcher.class,
                WebsitePageReader.class);

        List<String> dependencies = new ArrayList<>();
        for (Class<?> type : classes) {
            Stream<Class<?>> fieldTypes = Arrays.stream(type.getDeclaredFields()).map(Field::getType);
            Stream<Class<?>> parameterTypes = Arrays.stream(type.getDeclaredConstructors())
                    .map(Constructor::getParameterTypes).flatMap(Arrays::stream);
            Stream.concat(fieldTypes, parameterTypes).filter(dependency -> dependency.getName().startsWith(LLM_PACKAGE))
                    .forEach(dependency -> dependencies.add(type.getSimpleName() + " -> " + dependency.getName()));
        }
        assertThat(dependencies).isEmpty();
    }
}
