package it.scontrina.receipts.receipt;

import it.scontrina.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Evento pubblicato su receipts.documents per ogni documento emesso o annullato. */
public record ReceiptEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String receiptId,
        String organizationId,
        String registerId,
        long number,
        ReceiptType receiptType,
        String voidedReceiptId,
        String customerId,
        LocalDate businessDate,
        BigDecimal total,
        BigDecimal vatTotal
) implements DomainEvent {

    public static final String ISSUED = "ReceiptIssued";
    public static final String VOIDED = "ReceiptVoided";

    public static ReceiptEvent of(Receipt r) {
        return new ReceiptEvent(UUID.randomUUID().toString(),
                r.getType() == ReceiptType.SALE ? ISSUED : VOIDED,
                r.getIssuedAt(),
                r.getId(), r.getOrganizationId(), r.getRegisterId(), r.getNumber(), r.getType(),
                r.getVoidedReceiptId(), r.getCustomerId(), r.getBusinessDate(),
                r.getTotal(), r.getVatTotal());
    }
}
