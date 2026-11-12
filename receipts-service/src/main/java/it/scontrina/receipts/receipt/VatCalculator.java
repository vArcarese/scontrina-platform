package it.scontrina.receipts.receipt;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Calcolo di totali e IVA lato server. Funzione pura: niente DB, niente stato, facile da testare.
 *
 * Regola di arrotondamento:
 *  1. lineTotal = quantity * unitPrice, arrotondato a 2 decimali HALF_UP
 *  2. le righe si sommano PER ALIQUOTA -> gross dell'aliquota
 *  3. IVA scorporata sul totale dell'aliquota (non riga per riga):
 *       taxable = gross * 100 / (100 + aliquota), arrotondato a 2 decimali HALF_UP
 *       vat     = gross - taxable            (così gross = taxable + vat, al centesimo, sempre)
 *  4. totale documento = somma dei gross; IVA documento = somma delle vat
 */
@Component
public class VatCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public VatCalculation calculate(List<LineInput> lines) {
        List<ReceiptLine> receiptLines = new ArrayList<>();
        // EnumMap: breakdown sempre nell'ordine dell'enum (4, 10, 22), quindi output deterministico
        Map<VatRate, BigDecimal> grossByRate = new EnumMap<>(VatRate.class);

        for (LineInput line : lines) {
            BigDecimal lineTotal = line.quantity().multiply(line.unitPrice()).setScale(SCALE, ROUNDING);
            receiptLines.add(new ReceiptLine(line.description(), line.quantity(), line.unitPrice(),
                    line.vatRate(), lineTotal));
            grossByRate.merge(line.vatRate(), lineTotal, BigDecimal::add);
        }

        List<VatBreakdown> breakdown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal vatTotal = BigDecimal.ZERO.setScale(SCALE);

        for (Map.Entry<VatRate, BigDecimal> entry : grossByRate.entrySet()) {
            BigDecimal gross = entry.getValue();
            // un solo arrotondamento: gross * 100 / (100 + aliquota)
            BigDecimal taxable = gross.multiply(HUNDRED)
                    .divide(HUNDRED.add(entry.getKey().asBigDecimal()), SCALE, ROUNDING);
            BigDecimal vat = gross.subtract(taxable);   // per differenza: gross = taxable + vat, sempre

            breakdown.add(new VatBreakdown(entry.getKey(), gross, taxable, vat));
            total = total.add(gross);
            vatTotal = vatTotal.add(vat);
        }

        return new VatCalculation(List.copyOf(receiptLines), List.copyOf(breakdown), total, vatTotal);
    }

    public record LineInput(String description, BigDecimal quantity, BigDecimal unitPrice, VatRate vatRate) {
    }

    /**
     * @param lines     righe con lineTotal calcolato
     * @param breakdown un elemento per ogni aliquota presente
     * @param total     totale documento (IVA inclusa)
     * @param vatTotal  IVA totale
     */
    public record VatCalculation(List<ReceiptLine> lines, List<VatBreakdown> breakdown,
                                 BigDecimal total, BigDecimal vatTotal) {
    }
}
