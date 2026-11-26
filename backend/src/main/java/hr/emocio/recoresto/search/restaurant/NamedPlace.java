package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;

/**
 * A place of the model answer (spec 008 data-model.md {@code NamedPlace}). {@code position} is null when the answer
 * gave none or an invalid one; such a place must be located by the backend (FR-006).
 */
public record NamedPlace(String name, Position position) {
}
