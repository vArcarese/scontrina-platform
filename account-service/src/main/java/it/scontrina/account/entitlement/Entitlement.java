package it.scontrina.account.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Abilitazione di un'organizzazione a un prodotto. Una sola riga per (organizzazione, prodotto):
 * vincolo UNIQUE a DB, è la base dell'idempotenza del provisioning.
 */
@Entity
@Table(name = "entitlement")
@Getter
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntitlementStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** null = senza scadenza. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Entitlement() {
    }

    public static Entitlement create(String organizationId, Product product, EntitlementStatus status,
                                     LocalDate validFrom, LocalDate validTo, Instant now) {
        Entitlement e = new Entitlement();
        e.organizationId = organizationId;
        e.product = product;
        e.status = status;
        e.validFrom = validFrom;
        e.validTo = validTo;
        e.updatedAt = now;
        return e;
    }

    public boolean apply(EntitlementStatus status, LocalDate validFrom, LocalDate validTo, Instant now) {
        if (status == this.status && validFrom.equals(this.validFrom) && Objects.equals(validTo, this.validTo)) {
            return false;
        }
        this.status = status;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.updatedAt = now;
        return true;
    }

    /** Abilitata e dentro il periodo di validità alla data indicata. */
    public boolean isActiveOn(LocalDate date) {
        return status == EntitlementStatus.ENABLED
                && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo));
    }
}
