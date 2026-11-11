package it.scontrina.receipts.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;

/**
 * Contatore del progressivo per punto cassa. Una riga per (organizzazione, punto cassa).
 * È la riga che si blocca (SELECT ... FOR UPDATE) per serializzare le emissioni sulla stessa cassa.
 */
@Entity
@Table(name = "register_counter")
@Getter
public class RegisterCounter {

    @EmbeddedId
    private Key id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected RegisterCounter() {
    }

    public RegisterCounter(Key id) {
        this.id = id;
        this.lastNumber = 0;
    }

    /** Da chiamare SOLO con la riga bloccata, dentro la transazione che salva il documento. */
    public long next() {
        return ++lastNumber;
    }

    @Embeddable
    public record Key(@Column(name = "organization_id") String organizationId,
                      @Column(name = "register_id") String registerId) implements Serializable {
    }
}
