package it.scontrina.receipts.receipt;

import it.scontrina.common.error.BusinessException;
import it.scontrina.outbox.OutboxWriter;
import it.scontrina.receipts.access.AccessChecker;
import it.scontrina.receipts.config.ReceiptsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receipts;
    private final RegisterCounterRepository counters;
    private final IdempotencyRecordRepository idempotency;
    private final AccessChecker accessChecker;
    private final VatCalculator vatCalculator;
    private final OutboxWriter outbox;
    private final ReceiptsProperties properties;
    private final Clock clock;

    /**
     * Emissione di un documento di vendita.
     */
    @Transactional
    public IssueResult issue(String organizationId, String idempotencyKey, IssueReceiptCommand command) {
        // TODO: emissione con idempotency key, controllo abilitazione, calcolo IVA e numerazione
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Annullamento: nuovo documento VOID collegato all'originale. Anche questo idempotente.
     */
    @Transactional
    public IssueResult voidReceipt(String organizationId, String receiptId, String registerId,
                                   String idempotencyKey) {
        // TODO: annullamento idempotente con un nuovo documento VOID
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional(readOnly = true)
    public Receipt get(String organizationId, String receiptId) {
        return receipts.findByIdAndOrganizationId(receiptId, organizationId)
                .orElseThrow(() -> BusinessException.notFound("Documento", receiptId));
    }

    public record IssueReceiptCommand(String registerId, List<Line> lines, PaymentType paymentType,
                                      String customerId) {
        public record Line(String description, BigDecimal quantity, BigDecimal unitPrice, VatRate vatRate) {
        }
    }

    /** @param replayed true se il documento esisteva già (richiesta ripetuta con la stessa chiave). */
    public record IssueResult(Receipt receipt, boolean replayed) {
    }
}
