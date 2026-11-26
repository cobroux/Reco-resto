package hr.emocio.recoresto.search.google;

/** An image fetched from Google Place Photos (spec 008 D16 a), served as is by the backend photo address. */
public record GooglePhoto(byte[] bytes, String contentType) {
}
