package it.scontrina.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Smoke test: il contesto parte su MySQL e Kafka reali e le migration Flyway si applicano. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReceiptsApplicationIT {

    @Test
    void contextLoads() {
    }
}
