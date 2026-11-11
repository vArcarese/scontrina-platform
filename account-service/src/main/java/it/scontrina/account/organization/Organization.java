package it.scontrina.account.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization")
@Getter
public class Organization {

    @Id
    private String id;

    /** Partita IVA: chiave naturale, UNIQUE a DB. */
    @Column(name = "vat_number", nullable = false, unique = true)
    private String vatNumber;

    /** Ragione sociale. */
    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationStatus status;

    /**
     * Optimistic locking; usata anche come versione negli eventi (ordinamento lato consumer).
     * Long e non long: con l'id assegnato da noi, Spring Data capisce che l'entità è nuova
     * dalla version null, e fa persist (INSERT) invece di merge (SELECT + INSERT).
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    public static Organization register(String vatNumber, String legalName, Instant now) {
        Organization o = new Organization();
        o.id = UUID.randomUUID().toString();
        o.vatNumber = vatNumber;
        o.legalName = legalName;
        o.status = OrganizationStatus.ACTIVE;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }

    /** @return true se lo stato è effettivamente cambiato. */
    public boolean changeStatus(OrganizationStatus newStatus, Instant now) {
        if (this.status == newStatus) {
            return false;
        }
        this.status = newStatus;
        this.updatedAt = now;
        return true;
    }
}
