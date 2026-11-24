package hr.emocio.recoresto.search.place;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaceResolver {

    private final ApiAdresseClient client;

    public PlaceResolver(ApiAdresseClient client) {
        this.client = client;
    }

    /** Resolves every place name; names that cannot be resolved are omitted. */
    public List<ResolvedPlace> resolve(List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream()
                .flatMap(name -> client.geocode(name).map(position -> new ResolvedPlace(name, position)).stream())
                .toList();
    }
}
