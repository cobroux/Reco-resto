package hr.emocio.recoresto.search.api;

import hr.emocio.recoresto.search.SearchService;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    public SearchResult search(@RequestBody SearchRequest request,
            @RequestParam(defaultValue = "false") boolean includeZone) {
        return service.search(request, includeZone);
    }
}
