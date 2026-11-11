package it.scontrina.receipts.receipt;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

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

    public VatCalculation calculate(List<LineInput> lines) {
        // TODO: implementare la regola sopra, solo BigDecimal (mai double)
        throw new UnsupportedOperationException("TODO");
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
