package it.scontrina.account.entitlement;

import it.scontrina.account.config.KafkaTopics;
import it.scontrina.account.organization.Organization;
import it.scontrina.account.organization.OrganizationService;
import it.scontrina.common.error.BusinessException;
import it.scontrina.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementRepository repository;
    private final OrganizationService organizations;
    private final OutboxWriter outbox;
    private final Clock clock;

    /**
     * Provisioning idempotente: imposta lo stato DESIDERATO dell'abilitazione (semantica PUT).
     */
    @Transactional
    public Entitlement provision(String organizationId, Product product, EntitlementStatus status,
                                 LocalDate validFrom, LocalDate validTo) {

        Organization organization = organizations.get(organizationId);
        if (validFrom == null) {
            throw BusinessException.unprocessable("MISSING_VALID_FROM", "validFrom è obbligatorio");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw BusinessException.unprocessable("INVALID_VALIDITY_PERIOD",
                    "validTo (" + validTo + ") precede validFrom (" + validFrom + ")");
        }

        Instant now = clock.instant();
        Optional<Entitlement> existing = repository.findByOrganizationIdAndProduct(organization.getId(), product);
        Entitlement entitlement;
        if (existing.isEmpty()) {
            entitlement = Entitlement.create(organization.getId(), product, status, validFrom, validTo, now);
        } else {
            entitlement = existing.get();
            boolean changed = entitlement.apply(status, validFrom, validTo, now);
            if (!changed) {
                return entitlement;
            }
        }
        Entitlement savedEntitlement = repository.saveAndFlush(entitlement);
        outbox.append(KafkaTopics.ENTITLEMENTS, KafkaTopics.entitlementKey(organization.getId(), product), EntitlementChangedEvent.of(savedEntitlement, now));
        return savedEntitlement;
    }

    @Transactional(readOnly = true)
    public List<Entitlement> list(String organizationId) {
        organizations.get(organizationId);
        return repository.findByOrganizationId(organizationId);
    }
}
