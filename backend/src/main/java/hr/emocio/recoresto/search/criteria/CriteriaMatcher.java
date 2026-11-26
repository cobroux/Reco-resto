package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Evaluates the AND/OR tree of the criteria on a Google place (spec 008, D7) and holds the single reading of that tree:
 * <ul>
 * <li>the tree is {@code combination}; without it, OR between the cuisines and AND with the diets (decision Q2);</li>
 * <li>an operator node is OR when its operator is "OR" (any case), else AND; a node without children is no
 * constraint;</li>
 * <li>a cuisine leaf matches when the place types contain that Google type (D7);</li>
 * <li>a diet leaf is satisfied only when the place states that diet (D8, FR-009): an unknown diet excludes the place
 * under AND, and a diet Google never states matches no place.</li>
 * </ul>
 */
@Component
public class CriteriaMatcher {

    static final String CUISINE = "cuisine";
    static final String DIET = "diet";
    private static final String OR = "OR";
    private static final String AND = "AND";

    /** Whether the place satisfies the criteria; no criteria matches every place. */
    public boolean matches(SearchCriteria criteria, GooglePlace place) {
        return matches(tree(criteria), place);
    }

    private boolean matches(CriteriaNode node, GooglePlace place) {
        if (node == null) {
            return true;
        }
        if (isOperator(node)) {
            if (children(node).isEmpty()) {
                return true;
            }
            return isOr(node) ? children(node).stream().anyMatch(child -> matches(child, place))
                    : children(node).stream().allMatch(child -> matches(child, place));
        }
        if (CUISINE.equals(node.kind())) {
            return place.types().contains(node.value());
        }
        if (DIET.equals(node.kind())) {
            return node.value() != null && GooglePlaceTypes.statedDiets(place.types(), place.servesVegetarianFood())
                    .contains(node.value().trim().toLowerCase(Locale.ROOT));
        }
        return true; // any other kind: no constraint
    }

    /** The AND/OR tree of the criteria (decision Q2 when the model gave lists only); null when there is none. */
    public static CriteriaNode tree(SearchCriteria criteria) {
        if (criteria.combination() != null) {
            return criteria.combination();
        }
        if (criteria.cuisines().isEmpty() && criteria.diets().isEmpty()) {
            return null;
        }
        List<CriteriaNode> children = new ArrayList<>();
        if (!criteria.cuisines().isEmpty()) {
            children.add(new CriteriaNode(OR, null, null, criteria.cuisines().stream()
                    .map(cuisine -> new CriteriaNode(null, CUISINE, cuisine, null)).toList()));
        }
        criteria.diets().forEach(diet -> children.add(new CriteriaNode(null, DIET, diet, null)));
        return new CriteriaNode(AND, null, null, children);
    }

    /** Every cuisine value of the criteria, from the lists and the tree. */
    public static Set<String> cuisineValues(SearchCriteria criteria) {
        Set<String> values = new LinkedHashSet<>(criteria.cuisines());
        collectCuisines(criteria.combination(), values);
        return values;
    }

    private static void collectCuisines(CriteriaNode node, Set<String> values) {
        if (node == null) {
            return;
        }
        if (CUISINE.equals(node.kind()) && !isOperator(node)) {
            values.add(node.value());
        }
        children(node).forEach(child -> collectCuisines(child, values));
    }

    /**
     * The cuisine types one of which every matching place must have, so that the source can ask Google for them only;
     * empty when any restaurant may match (no cuisine, or an OR branch without cuisine constraint such as a diet,
     * decision Q1). Under AND, the smallest constrained child is enough.
     */
    public static Optional<Set<String>> requiredCuisineTypes(SearchCriteria criteria) {
        return required(tree(criteria));
    }

    private static Optional<Set<String>> required(CriteriaNode node) {
        if (node == null) {
            return Optional.empty();
        }
        if (isOperator(node)) {
            List<Optional<Set<String>>> children = children(node).stream().map(CriteriaMatcher::required).toList();
            if (isOr(node)) {
                if (children.isEmpty() || children.stream().anyMatch(Optional::isEmpty)) {
                    return Optional.empty();
                }
                Set<String> union = new LinkedHashSet<>();
                children.forEach(child -> union.addAll(child.orElseThrow()));
                return Optional.of(union);
            }
            return children.stream().flatMap(Optional::stream).min(Comparator.comparingInt(Set::size));
        }
        return CUISINE.equals(node.kind()) && node.value() != null ? Optional.of(Set.of(node.value()))
                : Optional.empty();
    }

    private static List<CriteriaNode> children(CriteriaNode node) {
        return node.children() == null ? List.of() : node.children();
    }

    private static boolean isOperator(CriteriaNode node) {
        return node.operator() != null || (node.kind() == null && node.children() != null);
    }

    private static boolean isOr(CriteriaNode node) {
        return OR.equalsIgnoreCase(node.operator());
    }
}
