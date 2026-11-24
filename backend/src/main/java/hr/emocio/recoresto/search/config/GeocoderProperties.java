package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("geocoder")
public record GeocoderProperties(String baseUrl) {
}
