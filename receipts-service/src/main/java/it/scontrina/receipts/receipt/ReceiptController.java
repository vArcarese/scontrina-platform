package it.scontrina.receipts.receipt;

import it.scontrina.common.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    /** Lunghezza della colonna idempotency_record.idempotency_key. */
    private static final int MAX_KEY_LENGTH = 64;

    private final ReceiptService service;

    /** 201 alla prima emissione, 200 se la richiesta è una ripetizione (stessa Idempotency-Key). */
    @PostMapping
    public ResponseEntity<ReceiptResponse> issue(@PathVariable String organizationId,
                                                 @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                 @Valid @RequestBody IssueReceiptRequest request) {
        requireValidKey(idempotencyKey);
        var result = service.issue(organizationId, idempotencyKey, request.toCommand());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ReceiptResponse.from(result.receipt()));
    }

    @GetMapping("/{receiptId}")
    public ReceiptResponse get(@PathVariable String organizationId, @PathVariable String receiptId) {
        return ReceiptResponse.from(service.get(organizationId, receiptId));
    }

    @PostMapping("/{receiptId}/void")
    public ResponseEntity<ReceiptResponse> voidReceipt(@PathVariable String organizationId,
                                                       @PathVariable String receiptId,
                                                       @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                       @Valid @RequestBody VoidReceiptRequest request) {
        requireValidKey(idempotencyKey);
        var result = service.voidReceipt(organizationId, receiptId, request.registerId(), idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ReceiptResponse.from(result.receipt()));
    }

    /**
     * Controllo fatto a mano e non con @Size sul parametro: un vincolo messo direttamente su un parametro
     * del metodo attiva la "method validation" di Spring, e a quel punto anche gli errori del body
     * uscirebbero in un formato diverso (senza l'elenco dei campi) da quello degli altri controller.
     */
    private static void requireValidKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    IDEMPOTENCY_HEADER + " deve avere da 1 a " + MAX_KEY_LENGTH + " caratteri");
        }
    }

    // ---------- DTO ----------

    // i @Size corrispondono alla lunghezza delle colonne: un valore troppo lungo è un 400, non un errore del database

    public record IssueReceiptRequest(
            @NotBlank @Size(max = 50) String registerId,
            @NotEmpty @Valid List<LineRequest> lines,
            @NotNull PaymentType paymentType,
            @Size(max = 64) String customerId) {

        ReceiptService.IssueReceiptCommand toCommand() {
            return new ReceiptService.IssueReceiptCommand(registerId,
                    lines.stream().map(l -> new ReceiptService.IssueReceiptCommand.Line(
                            l.description(), l.quantity(), l.unitPrice(), l.vatRate())).toList(),
                    paymentType, customerId);
        }
    }

    public record LineRequest(
            @NotBlank @Size(max = 255) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull VatRate vatRate) {
    }

    public record VoidReceiptRequest(@NotBlank @Size(max = 50) String registerId) {
    }

    public record ReceiptResponse(String id, String organizationId, String registerId, long number,
                                  ReceiptType type, String voidedReceiptId, PaymentType paymentType,
                                  String customerId, LocalDate businessDate, Instant issuedAt,
                                  BigDecimal total, BigDecimal vatTotal,
                                  List<LineResponse> lines, List<VatResponse> vatBreakdown) {

        static ReceiptResponse from(Receipt r) {
            return new ReceiptResponse(r.getId(), r.getOrganizationId(), r.getRegisterId(), r.getNumber(),
                    r.getType(), r.getVoidedReceiptId(), r.getPaymentType(), r.getCustomerId(),
                    r.getBusinessDate(), r.getIssuedAt(), r.getTotal(), r.getVatTotal(),
                    r.getLines().stream().map(l -> new LineResponse(l.getDescription(), l.getQuantity(),
                            l.getUnitPrice(), l.getVatRate(), l.getLineTotal())).toList(),
                    r.getVatBreakdown().stream().map(v -> new VatResponse(v.getVatRate(), v.getGross(),
                            v.getTaxable(), v.getVat())).toList());
        }
    }

    public record LineResponse(String description, BigDecimal quantity, BigDecimal unitPrice,
                               VatRate vatRate, BigDecimal lineTotal) {
    }

    public record VatResponse(VatRate vatRate, BigDecimal gross, BigDecimal taxable, BigDecimal vat) {
    }
}
