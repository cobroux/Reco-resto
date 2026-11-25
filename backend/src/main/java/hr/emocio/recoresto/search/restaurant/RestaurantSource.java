package hr.emocio.recoresto.search.restaurant;

/** Where restaurants come from; today the language model querying Overpass, replaceable without touching the rest. */
public interface RestaurantSource {

    /** Understands the sentence and finds the restaurants; see {@link SearchAnswer}. */
    SearchAnswer find(String sentence);
}
