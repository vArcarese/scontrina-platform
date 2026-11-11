package it.scontrina.receipts.closure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Chiusura giornaliera di un punto cassa. UNIQUE (organization_id, register_id, business_date):
 * una sola chiusura per cassa e giorno, anche se la richiesta di chiusura arriva due volte.
 */
@Entity
@Table(name = "daily_closure")
@Getter
public class DailyClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "register_id", nullable = false)
    private String registerId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** Identificativo inviato all'ente esterno: gli permette di deduplicare i nostri retry. */
    @Column(name = "transmission_id", nullable = false, unique = true)
    private String transmissionId;

    @Column(name = "receipt_count", nullable = false)
    private int receiptCount;

    @Column(name = "first_number")
    private Long firstNumber;

    @Column(name = "last_number")
    private Long lastNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClosureStatus status;

    @Column(nullable = false)
    private int attempts;

    /** Il job non riprova prima di questo istante (backoff). */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /** Fino a quando l'istanza che ha in carico la chiusura ne è "proprietaria". */
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** Riferimento restituito dall'ente esterno a trasmissione riuscita. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "transmitted_at")
    private Instant transmittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyClosure() {
    }

    public static DailyClosure open(String organizationId, String registerId, LocalDate businessDate,
                                    int receiptCount, Long firstNumber, Long lastNumber,
                                    BigDecimal total, Instant now) {
        DailyClosure c = new DailyClosure();
        c.organizationId = organizationId;
        c.registerId = registerId;
        c.businessDate = businessDate;
        c.transmissionId = UUID.randomUUID().toString();
        c.receiptCount = receiptCount;
        c.firstNumber = firstNumber;
        c.lastNumber = lastNumber;
        c.total = total;
        c.status = ClosureStatus.TO_TRANSMIT;
        c.attempts = 0;
        c.nextAttemptAt = now;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    // TODO: metodi di transizione della macchina a stati
}
