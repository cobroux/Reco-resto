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

    /** A latitude/longitude rectangle: its south-west ({@code low}) and north-east ({@code high}) corners. */
    public record Box(Position low, Position high) {
    }

    /**
     * The smallest latitude/longitude rectangle holding the circle of this center and radius (Google Text Search
     * restricts a search to a rectangle only). Its corners are outside the circle: the radius filter removes them.
     * Kept on Earth: latitudes within -90..90; when the circle reaches a pole, every longitude.
     */
    public Box boundingBox(double radiusMeters) {
        double angle = radiusMeters / EARTH_RADIUS_METERS;
        double latitudeDelta = Math.toDegrees(angle);
        double sine = Math.sin(angle) / Math.cos(Math.toRadians(latitude));
        if (sine >= 1 || Double.isNaN(sine)) {
            return new Box(new Position(Math.max(-90, latitude - latitudeDelta), -180),
                    new Position(Math.min(90, latitude + latitudeDelta), 180));
        }
        double longitudeDelta = Math.toDegrees(Math.asin(sine));
        return new Box(
                new Position(Math.max(-90, latitude - latitudeDelta), Math.max(-180, longitude - longitudeDelta)),
                new Position(Math.min(90, latitude + latitudeDelta), Math.min(180, longitude + longitudeDelta)));
    }
}
