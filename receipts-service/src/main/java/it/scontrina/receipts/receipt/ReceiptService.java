package it.scontrina.receipts.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.scontrina.common.error.BusinessException;
import it.scontrina.outbox.OutboxWriter;
import it.scontrina.receipts.access.AccessChecker;
import it.scontrina.receipts.config.KafkaTopics;
import it.scontrina.receipts.config.ReceiptsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final ObjectMapper mapper;

    /**
     * Emissione di un documento di vendita.
     */
    @Transactional
    public IssueResult issue(String organizationId, String idempotencyKey, IssueReceiptCommand command) {
        Instant now = clock.instant();
        LocalDate businessDate = LocalDate.ofInstant(now, properties.businessZone());
        IdempotencyRecord.Key key = new IdempotencyRecord.Key(organizationId, idempotencyKey);

        //idempotenza
        String requestHash = hash(command);
        Optional<IssueResult> replay = findReplay(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        //abilitazione
        accessChecker.ensureCanIssue(organizationId, businessDate);

        //importi
        List<VatCalculator.LineInput> linesInput = command.lines().stream()
                .map(line ->  new VatCalculator.LineInput(
                    line.description(),
                    line.quantity(),
                    line.unitPrice(),
                    line.vatRate())
                ).toList();
        VatCalculator.VatCalculation calculated = vatCalculator.calculate(linesInput);

        counters.insertIfAbsent(organizationId, command.registerId());
        RegisterCounter registerCounter = counters.findForUpdate(new RegisterCounter.Key(organizationId, command.registerId()));
        long number = registerCounter.next();

        Receipt saleReceipt = Receipt.sale(organizationId, command.registerId(), number, command.paymentType(), command.customerId(), businessDate, now, calculated);
        Receipt savedReceipt = receipts.saveAndFlush(saleReceipt);
        idempotency.saveAndFlush(new IdempotencyRecord(key, requestHash, savedReceipt.getId(), now));
        outbox.append(KafkaTopics.DOCUMENTS, organizationId, ReceiptEvent.of(savedReceipt));

        return new IssueResult(savedReceipt, false);
    }

    /**
     * Annullamento: nuovo documento VOID collegato all'originale. Anche questo idempotente.
     */
    @Transactional
    public IssueResult voidReceipt(String organizationId, String receiptId, String registerId,
                                   String idempotencyKey) {
        Instant now = clock.instant();
        LocalDate businessDate = LocalDate.ofInstant(now, properties.businessZone());
        IdempotencyRecord.Key key = new IdempotencyRecord.Key(organizationId, idempotencyKey);

        //idempotenza
        String requestHash = hash(List.of(receiptId, registerId));
        Optional<IssueResult> replay = findReplay(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Receipt original = receipts.findByIdAndOrganizationId(receiptId, organizationId)
                .orElseThrow(() -> BusinessException.notFound("Documento", receiptId));

        if (original.getType() != ReceiptType.SALE) {
            throw BusinessException.unprocessable("RECEIPT_NOT_VOIDABLE", "Documento non annullabile");
        }
        boolean existVoid = receipts.existsByVoidedReceiptId(original.getId());
        if (existVoid) {
            throw BusinessException.conflict("ALREADY_VOIDED","Il documento " + receiptId + " già annullato");
        }

        accessChecker.ensureCanIssue(organizationId, businessDate);

        counters.insertIfAbsent(organizationId, registerId);
        RegisterCounter registerCounter = counters.findForUpdate(new RegisterCounter.Key(organizationId, registerId));
        long number = registerCounter.next();

        Receipt voidReceipt = Receipt.voidOf(original, registerId, number, businessDate, now);
        Receipt savedVoid = receipts.saveAndFlush(voidReceipt);
        idempotency.saveAndFlush(new IdempotencyRecord(key, requestHash, savedVoid.getId(), now));
        outbox.append(KafkaTopics.DOCUMENTS, organizationId, ReceiptEvent.of(savedVoid));

        return new IssueResult(savedVoid, false);
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

    private String hash(Object command) {
        try {
            byte[] json = mapper.writeValueAsBytes(command);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest); // 64 caratteri come la colonna
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Impossibile calcolare l'hash della richiesta", e);
        }
    }

    private Optional<IssueResult> findReplay(IdempotencyRecord.Key key, String requestHash) {
        Optional<IdempotencyRecord> record = idempotency.findById(key);
        if (record.isPresent()) {
            if (Objects.equals(record.get().getRequestHash(), requestHash)) {
                Receipt existing = receipts.findById(record.get().getReceiptId())
                        .orElseThrow(() -> new IllegalStateException("Documento dell'idempotency record non trovato"));
                return Optional.of(new IssueResult(existing, true));
            } else {
                throw BusinessException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                        "La chiave di idempotenza è già stata usata per una richiesta diversa");
            }
        }
        return Optional.empty();
    }
}
