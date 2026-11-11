package it.scontrina.receipts.closure;

import it.scontrina.common.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}")
@RequiredArgsConstructor
public class ClosureController {

    private final ClosureService service;

    /** 202 Accepted: la chiusura è registrata, la trasmissione avviene in modo asincrono. */
    @PostMapping("/registers/{registerId}/closures")
    public ResponseEntity<ClosureResponse> close(@PathVariable String organizationId,
                                                 @PathVariable String registerId,
                                                 @Valid @RequestBody CloseRequest request) {
        var result = service.close(organizationId, registerId, request.businessDate());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(ClosureResponse.from(result.closure()));
    }

    /** Esito della trasmissione consultabile via API. */
    @GetMapping("/closures/{closureId}")
    public ClosureResponse get(@PathVariable String organizationId, @PathVariable Long closureId) {
        DailyClosure closure = service.get(closureId);
        if (!closure.getOrganizationId().equals(organizationId)) {
            throw BusinessException.notFound("Chiusura", closureId);
        }
        return ClosureResponse.from(closure);
    }

    @GetMapping("/closures")
    public List<ClosureResponse> list(@PathVariable String organizationId) {
        return service.list(organizationId).stream().map(ClosureResponse::from).toList();
    }

    public record CloseRequest(@NotNull LocalDate businessDate) {
    }

    public record ClosureResponse(Long id, String organizationId, String registerId, LocalDate businessDate,
                                  String transmissionId, int receiptCount, Long firstNumber, Long lastNumber,
                                  BigDecimal total, ClosureStatus status, int attempts, Instant nextAttemptAt,
                                  String lastError, String externalRef, Instant transmittedAt) {
        static ClosureResponse from(DailyClosure c) {
            return new ClosureResponse(c.getId(), c.getOrganizationId(), c.getRegisterId(), c.getBusinessDate(),
                    c.getTransmissionId(), c.getReceiptCount(), c.getFirstNumber(), c.getLastNumber(),
                    c.getTotal(), c.getStatus(), c.getAttempts(), c.getNextAttemptAt(),
                    c.getLastError(), c.getExternalRef(), c.getTransmittedAt());
        }
    }
}
