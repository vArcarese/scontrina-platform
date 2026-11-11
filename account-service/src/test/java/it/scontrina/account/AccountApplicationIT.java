package it.scontrina.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Smoke test: il contesto parte su MySQL e Kafka reali e le migration Flyway si applicano. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountApplicationIT {

    @Test
    void contextLoads() {
    }

    // TODO: test di provisioning idempotente e di pubblicazione degli eventi
}
