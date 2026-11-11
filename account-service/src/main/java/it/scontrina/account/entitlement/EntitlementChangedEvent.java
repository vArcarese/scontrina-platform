package it.scontrina.account.entitlement;

import it.scontrina.common.event.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Stato completo dell'abilitazione dopo il cambiamento + version per l'ordinamento lato consumer. */
public record EntitlementChangedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String organizationId,
        Product product,
        EntitlementStatus status,
        LocalDate validFrom,
        LocalDate validTo,
        long version
) implements DomainEvent {

    public static final String TYPE = "EntitlementChanged";

    public static EntitlementChangedEvent of(Entitlement e, Instant now) {
        return new EntitlementChangedEvent(UUID.randomUUID().toString(), TYPE, now,
                e.getOrganizationId(), e.getProduct(), e.getStatus(),
                e.getValidFrom(), e.getValidTo(), e.getVersion());
    }
}
