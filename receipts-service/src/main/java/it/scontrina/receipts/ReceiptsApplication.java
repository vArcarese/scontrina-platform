package it.scontrina.receipts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "it.scontrina")
@EntityScan("it.scontrina")
@EnableJpaRepositories("it.scontrina")
@ConfigurationPropertiesScan
@EnableScheduling
public class ReceiptsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiptsApplication.class, args);
    }
}
