package hr.emocio.recoresto.search.api;

public record Position(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    /** Whether the coordinates are a point on Earth (latitude -90..90, longitude -180..180). */
    public static boolean isValid(double latitude, double longitude) {
        return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    /** Great-circle (haversine) distance to another position, in meters. */
    public double distanceMetersTo(Position other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
