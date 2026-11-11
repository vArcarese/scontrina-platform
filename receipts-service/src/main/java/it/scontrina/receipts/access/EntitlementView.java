package it.scontrina.receipts.access;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;

/** Copia locale di un'abilitazione (organizzazione, prodotto). */
@Entity
@Table(name = "entitlement_view")
@Getter
public class EntitlementView {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private String status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    protected EntitlementView() {
    }

    public EntitlementView(Key id, String status, LocalDate validFrom, LocalDate validTo, long sourceVersion) {
        this.id = id;
        this.status = status;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.sourceVersion = sourceVersion;
    }

    public void update(String status, LocalDate validFrom, LocalDate validTo, long sourceVersion) {
        this.status = status;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.sourceVersion = sourceVersion;
    }

    public boolean isActiveOn(LocalDate date) {
        return "ENABLED".equals(status)
                && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo));
    }

    @Embeddable
    public record Key(@Column(name = "organization_id") String organizationId,
                      @Column(name = "product") String product) implements Serializable {
    }
}
