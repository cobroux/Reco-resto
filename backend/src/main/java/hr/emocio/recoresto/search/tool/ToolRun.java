package hr.emocio.recoresto.search.tool;

import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * One run of the restaurant search tool: what was searched, and the candidates in backend order.
 *
 * @param criteria what was searched
 * @param candidates the deck candidates, in backend order; the model refers to them as {@code r1}, {@code r2}...
 * @param zone every restaurant kept by the filters
 * @param rawById the Google answer of each candidate, by place id
 */
public record ToolRun(SearchCriteria criteria, List<Restaurant> candidates, List<Restaurant> zone,
        Map<String, JsonNode> rawById) {
}
