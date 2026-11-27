package hr.emocio.recoresto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RecoRestoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecoRestoApplication.class, args);
    }
}
