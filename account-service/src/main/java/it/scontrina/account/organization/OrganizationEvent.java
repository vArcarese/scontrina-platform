package it.scontrina.account.organization;

import it.scontrina.common.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento con lo stato COMPLETO dell'organizzazione (event-carried state transfer):
 * il consumer non deve ricostruire nulla, sostituisce la sua copia se version è più recente.
 */
public record OrganizationEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String organizationId,
        String legalName,
        OrganizationStatus status,
        long version
) implements DomainEvent {

    public static final String REGISTERED = "OrganizationRegistered";
    public static final String STATUS_CHANGED = "OrganizationStatusChanged";

    public static OrganizationEvent of(String type, Organization o, Instant now) {
        return new OrganizationEvent(UUID.randomUUID().toString(), type, now,
                o.getId(), o.getLegalName(), o.getStatus(), o.getVersion());
    }
}
