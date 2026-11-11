package it.scontrina.stub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "it.scontrina")
@ConfigurationPropertiesScan
public class TransmissionStubApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransmissionStubApplication.class, args);
    }
}
