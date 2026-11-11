package it.scontrina.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scanBasePackages = "it.scontrina": include i componenti delle librerie condivise
 * (platform-common, platform-outbox) oltre a quelli del servizio.
 */
@SpringBootApplication(scanBasePackages = "it.scontrina")
@EntityScan("it.scontrina")
@EnableJpaRepositories("it.scontrina")
@EnableScheduling
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
