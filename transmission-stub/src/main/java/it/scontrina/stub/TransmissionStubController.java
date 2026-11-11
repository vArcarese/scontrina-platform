package it.scontrina.stub;

import it.scontrina.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ente esterno simulato, stato in memoria.
 *
 * Comportamenti simulati:
 *  - 503 casuale (failureRate): la trasmissione NON viene registrata -> il client deve riprovare
 *  - risposta lenta (slowRate): la trasmissione VIENE registrata, poi la risposta tarda oltre il
 *    timeout del client. È il caso insidioso: il client vede un errore, ma l'ente ha ricevuto.
 *  - idempotenza su transmissionId: un retry di una trasmissione già registrata restituisce
 *    lo stesso externalRef (200) senza duplicarla. Il contatore "received" lo dimostra.
 */
@Slf4j
@RestController
@RequestMapping("/api/transmissions")
@RequiredArgsConstructor
public class TransmissionStubController {

    private final StubProperties properties;
    private final Map<String, StoredTransmission> store = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<TransmissionResponse> receive(@RequestBody TransmissionRequest request)
            throws InterruptedException {
        if (request.transmissionId() == null || request.transmissionId().isBlank()) {
            throw BusinessException.unprocessable("MISSING_TRANSMISSION_ID", "transmissionId obbligatorio");
        }
        var random = ThreadLocalRandom.current();

        if (random.nextDouble() < properties.failureRate()) {
            log.warn("Simulo indisponibilità transmissionId={}", request.transmissionId());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "Servizio temporaneamente non disponibile");
        }

        // computeIfAbsent: registrazione atomica, anche con richieste concorrenti sullo stesso id
        boolean[] isNew = {false};
        StoredTransmission stored = store.computeIfAbsent(request.transmissionId(), id -> {
            isNew[0] = true;
            return new StoredTransmission(request, "EXT-" + UUID.randomUUID(), Instant.now());
        });
        stored.received().incrementAndGet();
        log.info("Trasmissione {} transmissionId={} org={} register={} date={} receipts={} received={}",
                isNew[0] ? "registrata" : "già presente (duplicato)",
                request.transmissionId(), request.organizationId(), request.registerId(),
                request.businessDate(), request.receiptCount(), stored.received().get());

        if (random.nextDouble() < properties.slowRate()) {
            log.warn("Simulo risposta lenta ({}) transmissionId={}", properties.slowDelay(), request.transmissionId());
            Thread.sleep(properties.slowDelay());
        }

        var body = new TransmissionResponse(request.transmissionId(), stored.externalRef(), "ACCEPTED");
        return ResponseEntity.status(isNew[0] ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    /** Per i test e per la demo: quante volte è arrivata una trasmissione (deve restare registrata una sola volta). */
    @GetMapping("/{transmissionId}")
    public StoredTransmissionView get(@PathVariable String transmissionId) {
        StoredTransmission t = store.get(transmissionId);
        if (t == null) {
            throw BusinessException.notFound("Trasmissione", transmissionId);
        }
        return new StoredTransmissionView(transmissionId, t.externalRef(), t.registeredAt(), t.received().get());
    }

    public record TransmissionRequest(String transmissionId, String organizationId, String registerId,
                                      LocalDate businessDate, int receiptCount, Long firstNumber,
                                      Long lastNumber, BigDecimal total) {
    }

    public record TransmissionResponse(String transmissionId, String externalRef, String status) {
    }

    public record StoredTransmissionView(String transmissionId, String externalRef,
                                         Instant registeredAt, int timesReceived) {
    }

    private record StoredTransmission(TransmissionRequest request, String externalRef, Instant registeredAt,
                                      AtomicInteger received) {
        StoredTransmission(TransmissionRequest request, String externalRef, Instant registeredAt) {
            this(request, externalRef, registeredAt, new AtomicInteger());
        }
    }
}
