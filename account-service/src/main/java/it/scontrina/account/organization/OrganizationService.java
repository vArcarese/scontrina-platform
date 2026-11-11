package it.scontrina.account.organization;

import it.scontrina.account.config.KafkaTopics;
import it.scontrina.common.error.BusinessException;
import it.scontrina.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository repository;
    private final OutboxWriter outbox;
    private final Clock clock;

    /** Registra l'organizzazione e accoda l'evento nella stessa transazione (outbox). */
    @Transactional
    public Organization register(String vatNumber, String legalName) {
        repository.findByVatNumber(vatNumber).ifPresent(existing -> {
            throw BusinessException.conflict("ORGANIZATION_ALREADY_EXISTS",
                    "Organizzazione con partita IVA " + vatNumber + " già registrata: " + existing.getId());
        });
        Instant now = clock.instant();
        // saveAndFlush: forza l'INSERT ora, così una violazione del vincolo UNIQUE su vat_number
        // (due registrazioni concorrenti) emerge qui e diventa 409 (GlobalExceptionHandler).
        Organization org = repository.saveAndFlush(Organization.register(vatNumber, legalName, now));
        outbox.append(KafkaTopics.ORGANIZATIONS, org.getId(),
                OrganizationEvent.of(OrganizationEvent.REGISTERED, org, now));
        return org;
    }

    @Transactional(readOnly = true)
    public Organization get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Organizzazione", id));
    }

    @Transactional(readOnly = true)
    public List<Organization> list() {
        return repository.findAll();
    }

    @Transactional
    public Organization changeStatus(String id, OrganizationStatus newStatus) {
        Organization organization = this.get(id);
        Instant now = clock.instant();
        boolean changed = organization.changeStatus(newStatus, now);
        if (!changed) {
            return organization;
        }
        Organization savedOrg = repository.saveAndFlush(organization);
        outbox.append(KafkaTopics.ORGANIZATIONS, savedOrg.getId(),
                    OrganizationEvent.of(OrganizationEvent.STATUS_CHANGED, savedOrg, now));
        return savedOrg;
    }
}
