package hr.emocio.recoresto.search.mcp;

import hr.emocio.recoresto.search.SearchService;
import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** The tools of the "reco-resto" MCP server: the same search as {@code POST /api/search}, through {@link SearchService}. */
@Component
public class SearchMcpTools {

    private final SearchService service;

    public SearchMcpTools(SearchService service) {
        this.service = service;
    }

    @McpTool(name = "search_restaurants", description = """
            Finds restaurants in France from a sentence written in French (cuisine, diet, place, moment, distance or \
            walking time). Returns the criteria understood and the deck of restaurants.""")
    public SearchResult searchRestaurants(
            @McpToolParam(description = "The search, in French, for example: italien près d'Opéra ce soir") String sentence,
            @McpToolParam(description = "Latitude of the user, optional; give it with the longitude", required = false)
            Double latitude,
            @McpToolParam(description = "Longitude of the user, optional; give it with the latitude", required = false)
            Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be given together");
        }
        Position position = latitude == null ? null : new Position(latitude, longitude);
        return service.search(new SearchRequest(sentence, position), false);
    }
}
