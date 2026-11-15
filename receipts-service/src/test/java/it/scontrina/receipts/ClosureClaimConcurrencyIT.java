package it.scontrina.receipts;

import it.scontrina.receipts.closure.ClosureStatus;
import it.scontrina.receipts.closure.DailyClosure;
import it.scontrina.receipts.closure.DailyClosureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nessuna doppia trasmissione anche con più istanze: più claim concorrenti sulla stessa chiusura,
 * ne vince uno solo (l'UPDATE condizionale viene applicato una volta sola).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClosureClaimConcurrencyIT {

    private static final int THREADS = 10;

    @Autowired
    private DailyClosureRepository dailyClosureRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void test_givenOneClosure_whenTenConcurrentClaims_thenOnlyOneSucceeds() throws Exception {
        // given: una chiusura trasmissibile tra un'ora. Nel contesto gira anche il vero TransmissionJob:
        // con nextAttemptAt nel futuro il job la ignora e non interferisce con il test.
        // Istanti troncati ai microsecondi, la precisione delle colonne DATETIME(6).
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        DailyClosure closure = dailyClosureRepository.saveAndFlush(DailyClosure.open(
                UUID.randomUUID().toString(), "CASSA-1", LocalDate.now(), 0,
                null, null, new BigDecimal("0.00"), future));

        // i claim usano un "adesso" successivo a nextAttemptAt: per loro la chiusura è candidata
        Instant claimNow = future.plus(1, ChronoUnit.HOURS);
        Instant leaseUntil = claimNow.plusSeconds(30);

        // when: 10 claim in parallelo, ognuno nella sua transazione (come 10 istanze del job)
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                // claim è un @Modifying: serve una transazione, come nel job
                return transactionTemplate.execute(status ->
                        dailyClosureRepository.claim(closure.getId(), claimNow, leaseUntil));
            }));
        }
        start.countDown();

        List<Integer> results = new ArrayList<>();
        try {
            for (Future<Integer> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));   // rilancia l'eccezione del thread, se c'è
            }
        } finally {
            pool.shutdownNow();
        }

        // then: un solo claim riuscito, gli altri 9 hanno aggiornato 0 righe
        assertThat(results).containsOnlyOnce(1);
        assertThat(results).filteredOn(r -> r == 0).hasSize(THREADS - 1);

        // e a livello di dati l'UPDATE è stato applicato una volta sola
        DailyClosure claimed = dailyClosureRepository.findById(closure.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(ClosureStatus.IN_PROGRESS);
        assertThat(claimed.getAttempts()).isEqualTo(1);
        assertThat(claimed.getLeaseUntil()).isEqualTo(leaseUntil);
    }
}
