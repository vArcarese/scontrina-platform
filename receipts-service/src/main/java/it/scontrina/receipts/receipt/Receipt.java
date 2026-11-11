package it.scontrina.receipts.receipt;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Documento commerciale. IMMUTABILE dopo l'emissione: nessun setter.
 * L'annullamento è un nuovo documento di tipo VOID che punta all'originale.
 */
@Entity
@Table(name = "receipt")
@Getter
public class Receipt implements Persistable<String> {

    @Id
    private String id;

    /** Vedi IdempotencyRecord: con id assegnato, save() deve fare persist e non merge. */
    @Transient
    @Getter(AccessLevel.NONE)   // solo per Persistable.isNew(), non una proprietà da esporre
    private boolean isNew = true;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "register_id", nullable = false)
    private String registerId;

    /** Progressivo per (organizzazione, punto cassa), senza buchi. */
    @Column(nullable = false)
    private long number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptType type;

    /** Solo per i VOID: documento annullato. UNIQUE a DB -> non si annulla due volte. */
    @Column(name = "voided_receipt_id")
    private String voidedReceiptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "vat_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatTotal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "receipt_line", joinColumns = @JoinColumn(name = "receipt_id"))
    @OrderColumn(name = "line_no")
    private List<ReceiptLine> lines = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "receipt_vat", joinColumns = @JoinColumn(name = "receipt_id"))
    @OrderColumn(name = "position")
    private List<VatBreakdown> vatBreakdown = new ArrayList<>();

    protected Receipt() {
    }

    public static Receipt sale(String organizationId, String registerId, long number,
                               PaymentType paymentType, String customerId,
                               LocalDate businessDate, Instant issuedAt,
                               VatCalculator.VatCalculation calculation) {
        Receipt r = new Receipt();
        r.id = UUID.randomUUID().toString();
        r.organizationId = organizationId;
        r.registerId = registerId;
        r.number = number;
        r.type = ReceiptType.SALE;
        r.paymentType = paymentType;
        r.customerId = customerId;
        r.businessDate = businessDate;
        r.issuedAt = issuedAt;
        r.total = calculation.total();
        r.vatTotal = calculation.vatTotal();
        r.lines = new ArrayList<>(calculation.lines());
        r.vatBreakdown = new ArrayList<>(calculation.breakdown());
        return r;
    }

    /**
     * Documento di annullamento: stessi importi e righe dell'originale (positivi), tipo VOID.
     * Emesso sul punto cassa indicato, con il suo numero progressivo.
     */
    public static Receipt voidOf(Receipt original, String registerId, long number,
                                 LocalDate businessDate, Instant issuedAt) {
        if (original.type != ReceiptType.SALE) {
            throw new IllegalArgumentException("Si può annullare solo un documento di vendita");
        }
        Receipt r = new Receipt();
        r.id = UUID.randomUUID().toString();
        r.organizationId = original.organizationId;
        r.registerId = registerId;
        r.number = number;
        r.type = ReceiptType.VOID;
        r.voidedReceiptId = original.id;
        r.paymentType = original.paymentType;
        r.customerId = original.customerId;
        r.businessDate = businessDate;
        r.issuedAt = issuedAt;
        r.total = original.total;
        r.vatTotal = original.vatTotal;
        r.lines = new ArrayList<>(original.lines);
        r.vatBreakdown = new ArrayList<>(original.vatBreakdown);
        return r;
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

    /**
     * Scritti a mano (Lombok non genera un getter se il metodo esiste già):
     * restituiscono una copia immutabile, così dall'esterno non si possono modificare le righe.
     */
    public List<ReceiptLine> getLines() {
        return List.copyOf(lines);
    }

    public List<VatBreakdown> getVatBreakdown() {
        return List.copyOf(vatBreakdown);
    }
}
