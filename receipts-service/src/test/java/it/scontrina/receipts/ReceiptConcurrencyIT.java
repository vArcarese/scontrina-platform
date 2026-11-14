package it.scontrina.receipts;

import it.scontrina.receipts.access.EntitlementView;
import it.scontrina.receipts.access.EntitlementViewRepository;
import it.scontrina.receipts.access.OrganizationView;
import it.scontrina.receipts.access.OrganizationViewRepository;
import it.scontrina.receipts.receipt.PaymentType;
import it.scontrina.receipts.receipt.ReceiptService;
import it.scontrina.receipts.receipt.ReceiptService.IssueReceiptCommand;
import it.scontrina.receipts.receipt.ReceiptService.IssueResult;
import it.scontrina.receipts.receipt.RegisterCounter;
import it.scontrina.receipts.receipt.RegisterCounterRepository;
import it.scontrina.receipts.receipt.VatRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReceiptConcurrencyIT {

    private static final int THREADS = 20;

    @Autowired
    private OrganizationViewRepository organizationViewRepository;

    @Autowired
    private EntitlementViewRepository entitlementViewRepository;

    @Autowired
    private RegisterCounterRepository registerCounterRepository;

    @Autowired
    private ReceiptService receiptService;

    @Test
    void test_givenTwentyConcurrentIssues_whenSameRegister_thenNumbersAreOneToTwentyWithoutGapsOrDuplicates()
            throws Exception {
        // given: un'organizzazione attiva e abilitata nella copia locale
        String orgId = givenEnabledOrganization();
        String registerId = "CASSA-" + UUID.randomUUID();
        IssueReceiptCommand command = oneLineCommand(registerId);

        // when: 20 emissioni sulla stessa cassa, tutte rilasciate nello stesso istante
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                // chiave diversa per ogni richiesta: qui si testa la numerazione, non l'idempotenza
                return receiptService.issue(orgId, UUID.randomUUID().toString(), command).receipt().getNumber();
            }));
        }
        start.countDown();

        List<Long> numbers = new ArrayList<>();
        try {
            for (Future<Long> future : futures) {
                numbers.add(future.get(30, TimeUnit.SECONDS));   // rilancia l'eccezione del thread, se c'è
            }
        } finally {
            pool.shutdownNow();
        }

        // then: esattamente 1..20, senza buchi né duplicati, e il contatore fermo a 20
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, THREADS).boxed().toList());
        assertThat(registerCounterRepository.findById(new RegisterCounter.Key(orgId, registerId)))
                .hasValueSatisfying(counter -> assertThat(counter.getLastNumber()).isEqualTo(THREADS));
    }

    @Test
    void test_givenSameIdempotencyKey_whenTwentyConcurrentIssues_thenOnlyOneReceiptIsCreated() throws Exception {
        // given: stessa chiave e stesso contenuto per tutti (ripetizione legittima, non un 422)
        String orgId = givenEnabledOrganization();
        String registerId = "CASSA-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IssueReceiptCommand command = oneLineCommand(registerId);

        // when
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<IssueResult>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return receiptService.issue(orgId, key, command);
            }));
        }
        start.countDown();

        // qui alcune eccezioni sono attese (i perdenti sulla PK): si raccolgono invece di far fallire il test
        List<IssueResult> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        try {
            for (Future<IssueResult> future : futures) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    errors.add(e.getCause());      // l'eccezione vera lanciata dentro il thread
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // then: nessuna richiesta persa, un solo vincitore, tutti i successi sullo stesso documento
        assertThat(results.size() + errors.size()).isEqualTo(THREADS);
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(results).extracting(r -> r.receipt().getId()).containsOnly(results.get(0).receipt().getId());

        // i perdenti falliscono solo per la PK di idempotency_record (nell'API: 409)
        assertThat(errors).allSatisfy(e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class));

        // il rollback dei perdenti ha annullato anche il loro next(): un solo numero consumato
        assertThat(registerCounterRepository.findById(new RegisterCounter.Key(orgId, registerId)))
                .hasValueSatisfying(counter -> assertThat(counter.getLastNumber()).isEqualTo(1));
    }

    /** Organizzazione attiva e abilitata a SCONTRINA, scritta direttamente nella copia locale (niente Account, niente Kafka). */
    private String givenEnabledOrganization() {
        String orgId = UUID.randomUUID().toString();
        organizationViewRepository.save(new OrganizationView(orgId, "ACTIVE", 0));
        entitlementViewRepository.save(new EntitlementView(new EntitlementView.Key(orgId, "SCONTRINA"),
                "ENABLED", LocalDate.of(2026, 1, 1), null, 0));
        return orgId;
    }

    private static IssueReceiptCommand oneLineCommand(String registerId) {
        return new IssueReceiptCommand(registerId,
                List.of(new IssueReceiptCommand.Line("Caffè", BigDecimal.ONE, new BigDecimal("1.20"), VatRate.VAT_10)),
                PaymentType.CARD, null);
    }
}
