package it.scontrina.receipts.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Riga del documento: value object (nessuna identità propria), salvata come @ElementCollection.
 * unitPrice è IVA INCLUSA, come avviene sugli scontrini.
 */
@Embeddable
@Getter
public class ReceiptLine {

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_rate", nullable = false)
    private VatRate vatRate;

    /** quantity * unitPrice, arrotondato secondo la regola documentata in VatCalculator. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected ReceiptLine() {
    }

    public ReceiptLine(String description, BigDecimal quantity, BigDecimal unitPrice,
                       VatRate vatRate, BigDecimal lineTotal) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.lineTotal = lineTotal;
    }
}
