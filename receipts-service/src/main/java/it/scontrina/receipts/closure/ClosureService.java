package it.scontrina.receipts.closure;

import it.scontrina.common.error.BusinessException;
import it.scontrina.receipts.receipt.Receipt;
import it.scontrina.receipts.receipt.ReceiptRepository;
import it.scontrina.receipts.receipt.ReceiptType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClosureService {

    private final DailyClosureRepository closures;
    private final ReceiptRepository receipts;
    private final Clock clock;

    /**
     * Chiude la giornata di un punto cassa: raggruppa i documenti del giorno e crea la chiusura
     * in stato TO_TRANSMIT. La trasmissione vera la fa TransmissionJob, in modo asincrono.
     *
     * Idempotente: se la chiusura del giorno esiste già la restituisce (created = false).
     * Due chiusure concorrenti dello stesso giorno: vince il vincolo UNIQUE, l'altra riceve 409.
     *
     * receiptCount conta tutti i documenti (vendite e annullamenti), perché tutti consumano un numero;
     * total è l'incassato netto: somma delle vendite meno somma degli annullamenti.
     */
    @Transactional
    public ClosureResult close(String organizationId, String registerId, LocalDate businessDate) {
        Optional<DailyClosure> existing = closures.findByOrganizationIdAndRegisterIdAndBusinessDate(
                organizationId, registerId, businessDate);
        if (existing.isPresent()) {
            return new ClosureResult(existing.get(), false);
        }

        List<Receipt> dayReceipts = receipts.findByOrganizationIdAndRegisterIdAndBusinessDateOrderByNumber(
                organizationId, registerId, businessDate);

        // giornata senza documenti: nessun primo/ultimo numero (null)
        Long firstNumber = dayReceipts.isEmpty() ? null : dayReceipts.getFirst().getNumber();
        Long lastNumber = dayReceipts.isEmpty() ? null : dayReceipts.getLast().getNumber();

        BigDecimal totalSales = sumTotals(dayReceipts, ReceiptType.SALE);
        BigDecimal totalVoids = sumTotals(dayReceipts, ReceiptType.VOID);
        BigDecimal total = totalSales.subtract(totalVoids);

        DailyClosure closure = DailyClosure.open(organizationId, registerId, businessDate,
                dayReceipts.size(), firstNumber, lastNumber, total, clock.instant());
        DailyClosure saved = closures.saveAndFlush(closure);
        return new ClosureResult(saved, true);
    }

    @Transactional(readOnly = true)
    public DailyClosure get(Long id) {
        return closures.findById(id).orElseThrow(() -> BusinessException.notFound("Chiusura", id));
    }

    @Transactional(readOnly = true)
    public List<DailyClosure> list(String organizationId) {
        return closures.findByOrganizationIdOrderByBusinessDateDesc(organizationId);
    }

    public record ClosureResult(DailyClosure closure, boolean created) {
    }

    private static BigDecimal sumTotals(List<Receipt> receipts, ReceiptType type) {
        return receipts.stream()
                .filter(r -> r.getType() == type)
                .map(Receipt::getTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
