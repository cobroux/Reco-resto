package hr.emocio.recoresto.search.criteria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A node of the AND/OR tree of criteria: either a leaf ({@code kind} "cuisine" or "diet" and a {@code value}) or an
 * operator ("AND" or "OR") over {@code children}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CriteriaNode(String operator, String kind, String value, List<CriteriaNode> children) {
}
