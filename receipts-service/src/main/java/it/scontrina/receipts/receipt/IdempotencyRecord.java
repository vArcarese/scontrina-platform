package it.scontrina.receipts.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;

/**
 * Traccia una chiave di idempotenza già usata e il documento che ha prodotto.
 * PK (organizationId, idempotencyKey): due inserimenti con la stessa chiave -> uno fallisce.
 *
 * Persistable: con id assegnato da noi, Spring Data considererebbe l'entità "non nuova"
 * e save() farebbe merge, cioè UPDATE della riga esistente invece di violare la PK.
 * Qui vogliamo l'opposto: save() deve fare INSERT e fallire sui duplicati.
 */
@Entity
@Table(name = "idempotency_record")
@Getter
public class IdempotencyRecord implements Persistable<IdempotencyRecord.Key> {

    @Transient
    @Getter(AccessLevel.NONE)   // solo per Persistable.isNew(), non una proprietà da esporre
    private boolean isNew = true;

    @EmbeddedId
    private Key id;

    /** Hash del contenuto della richiesta: stessa chiave con payload diverso = errore del client. */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "receipt_id", nullable = false)
    private String receiptId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(Key id, String requestHash, String receiptId, Instant createdAt) {
        this.id = id;
        this.requestHash = requestHash;
        this.receiptId = receiptId;
        this.createdAt = createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Embeddable
    public record Key(@Column(name = "organization_id") String organizationId,
                      @Column(name = "idempotency_key") String idempotencyKey) implements Serializable {
    }
}
