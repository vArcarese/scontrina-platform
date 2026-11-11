package it.scontrina.receipts.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.math.BigDecimal;

/** Riepilogo per aliquota: totale lordo, imponibile e imposta. gross = taxable + vat. */
@Embeddable
@Getter
public class VatBreakdown {

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_rate", nullable = false)
    private VatRate vatRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxable;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vat;

    protected VatBreakdown() {
    }

    public VatBreakdown(VatRate vatRate, BigDecimal gross, BigDecimal taxable, BigDecimal vat) {
        this.vatRate = vatRate;
        this.gross = gross;
        this.taxable = taxable;
        this.vat = vat;
    }
}
