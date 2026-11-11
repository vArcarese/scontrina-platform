package it.scontrina.receipts.receipt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Arrays;

/** Aliquote IVA ammesse. In JSON si scrivono come numero: 4, 10, 22. */
public enum VatRate {
    VAT_4(4),
    VAT_10(10),
    VAT_22(22);

    private final int percent;

    VatRate(int percent) {
        this.percent = percent;
    }

    @JsonValue
    public int percent() {
        return percent;
    }

    public BigDecimal asBigDecimal() {
        return BigDecimal.valueOf(percent);
    }

    @JsonCreator
    public static VatRate fromPercent(int percent) {
        return Arrays.stream(values())
                .filter(r -> r.percent == percent)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Aliquota IVA non ammessa: " + percent));
    }
}
