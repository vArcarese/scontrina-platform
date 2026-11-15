package it.scontrina.account;

import it.scontrina.account.config.KafkaTopics;
import it.scontrina.account.entitlement.Entitlement;
import it.scontrina.account.entitlement.EntitlementRepository;
import it.scontrina.account.entitlement.EntitlementService;
import it.scontrina.account.entitlement.EntitlementStatus;
import it.scontrina.account.entitlement.Product;
import it.scontrina.account.organization.Organization;
import it.scontrina.account.organization.OrganizationService;
import it.scontrina.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrazione su MySQL e Kafka reali: il contesto parte, le migration Flyway si applicano
 * e il provisioning delle abilitazioni è idempotente (niente righe né eventi duplicati).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountApplicationIT {

    private static final LocalDate VALID_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate VALID_TO = LocalDate.of(2026, 12, 31);

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private EntitlementService entitlementService;

    @Autowired
    private EntitlementRepository entitlementRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void test_givenSameProvisioningRepeated_whenProvision_thenOneEntitlementAndOneEvent() {
        // given
        Organization org = givenOrganization();

        // when: la stessa richiesta tre volte
        Entitlement first = provision(org, VALID_TO);
        Entitlement second = provision(org, VALID_TO);
        Entitlement third = provision(org, VALID_TO);

        // then: una sola riga, e le ripetizioni non l'hanno modificata (version invariata)
        assertThat(entitlementRepository.findByOrganizationId(org.getId())).hasSize(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());
        assertThat(third.getVersion()).isEqualTo(first.getVersion());

        // e un solo evento: le ripetizioni non ne hanno generati
        assertThat(entitlementEvents(org)).isEqualTo(1);
    }

    @Test
    void test_givenProvisionedEntitlement_whenProvisionWithDifferentValidity_thenUpdatedAndNewEvent() {
        // given
        Organization org = givenOrganization();
        Entitlement first = provision(org, VALID_TO);

        // when: stessa abilitazione, data di fine diversa
        LocalDate newValidTo = LocalDate.of(2027, 6, 30);
        Entitlement updated = provision(org, newValidTo);

        // then: sempre una sola riga, aggiornata
        assertThat(entitlementRepository.findByOrganizationId(org.getId())).hasSize(1);
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getValidTo()).isEqualTo(newValidTo);
        assertThat(updated.getVersion()).isEqualTo(first.getVersion() + 1);

        // e un evento in più: uno per la creazione, uno per la modifica
        assertThat(entitlementEvents(org)).isEqualTo(2);
    }

    /** Organizzazione nuova con partita IVA casuale (11 cifre): il vincolo UNIQUE vale su tutto il database di test. */
    private Organization givenOrganization() {
        String vat = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L));
        return organizationService.register(vat, "Test provisioning");
    }

    private Entitlement provision(Organization org, LocalDate validTo) {
        return entitlementService.provision(org.getId(), Product.SCONTRINA, EntitlementStatus.ENABLED,
                VALID_FROM, validTo);
    }

    /** Eventi in outbox per l'abilitazione SCONTRINA dell'organizzazione (chiave del messaggio org:prodotto). */
    private long entitlementEvents(Organization org) {
        String key = KafkaTopics.entitlementKey(org.getId(), Product.SCONTRINA);
        return outboxRepository.findAll().stream()
                .filter(e -> e.getTopic().equals(KafkaTopics.ENTITLEMENTS) && e.getMessageKey().equals(key))
                .count();
    }
}
