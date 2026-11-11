package it.scontrina.receipts.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Copia locale (read model) dello stato di un'organizzazione, alimentata dagli eventi di Account.
 * NON è la fonte di verità: Account lo è. Qui serve per decidere se si può emettere
 * anche quando Account non è raggiungibile.
 */
@Entity
@Table(name = "organization_view")
@Getter
public class OrganizationView {

    @Id
    @Column(name = "organization_id")
    private String organizationId;

    /** ACTIVE / SUSPENDED, come stringa: Receipts non dipende dagli enum di Account. */
    @Column(nullable = false)
    private String status;

    /** Version dell'ultimo evento applicato: eventi con version <= vengono ignorati. */
    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    protected OrganizationView() {
    }

    public OrganizationView(String organizationId, String status, long sourceVersion) {
        this.organizationId = organizationId;
        this.status = status;
        this.sourceVersion = sourceVersion;
    }

    public void update(String status, long sourceVersion) {
        this.status = status;
        this.sourceVersion = sourceVersion;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
