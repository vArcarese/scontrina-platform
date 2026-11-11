package it.scontrina.receipts.access;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Contratti degli eventi di Account, così come li legge Receipts.
 * Sono copie volute (non una libreria condivisa): Receipts dichiara solo i campi che usa,
 * Account può aggiungere campi senza rompere il consumer (Jackson ignora i campi sconosciuti).
 */
public final class AccountEvents {

    private AccountEvents() {
    }

    public record OrganizationEvent(String eventId, String eventType, Instant occurredAt,
                                    String organizationId, String status, long version) {
    }

    public record EntitlementChangedEvent(String eventId, String eventType, Instant occurredAt,
                                          String organizationId, String product, String status,
                                          LocalDate validFrom, LocalDate validTo, long version) {
    }
}
