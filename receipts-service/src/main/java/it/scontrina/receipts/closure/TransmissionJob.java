package it.scontrina.receipts.closure;

import it.scontrina.common.correlation.CorrelationId;
import it.scontrina.receipts.closure.TransmissionClient.TransmissionOutcome;
import it.scontrina.receipts.closure.TransmissionClient.TransmissionOutcome.Accepted;
import it.scontrina.receipts.closure.TransmissionClient.TransmissionOutcome.PermanentFailure;
import it.scontrina.receipts.closure.TransmissionClient.TransmissionOutcome.TransientFailure;
import it.scontrina.receipts.config.ReceiptsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Job di trasmissione delle chiusure. Può girare su più istanze contemporaneamente:
 * la correttezza NON dipende dal fatto che giri una volta sola, ma dal claim atomico a DB.
 *
 * Per ogni chiusura candidata, tre passi separati:
 *  1. claim atomico (UPDATE condizionale) in una transazione breve: 1 riga = mia, 0 = di un'altra istanza
 *  2. invio HTTP FUORI da qualsiasi transazione: nessun lock aperto durante una chiamata di rete
 *  3. esito registrato in una nuova transazione breve (trasmessa / retry con backoff / fallita)
 *
 * Le transazioni brevi usano TransactionTemplate: un metodo @Transactional chiamato dalla stessa
 * classe non passerebbe dal proxy di Spring e non aprirebbe la transazione.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransmissionJob {

    private static final int BATCH_SIZE = 10;

    private final DailyClosureRepository closures;
    private final TransmissionClient client;
    private final ReceiptsProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Scheduled(fixedDelayString = "${receipts.transmission.poll-interval}")
    public void run() {
        List<Long> candidates = closures.findCandidateIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
        for (Long id : candidates) {
            try {
                process(id);
            } catch (RuntimeException e) {
                // un errore su una chiusura non deve fermare le altre: questa verrà ripresa a lease scaduto
                log.error("Errore inatteso nella trasmissione closureId={}", id, e);
            }
        }
    }

    private void process(Long id) {
        // 1. claim. Il lease è troncato ai microsecondi, la precisione della colonna DATETIME(6):
        //    così si può confrontare con il valore riletto dal DB al passo 3.
        Instant leaseUntil = clock.instant().plus(properties.transmission().lease()).truncatedTo(ChronoUnit.MICROS);
        DailyClosure closure = tx.execute(status -> {
            int claimed = closures.claim(id, clock.instant(), leaseUntil);
            return claimed == 1 ? closures.findById(id).orElseThrow() : null;
        });
        if (closure == null) {
            log.debug("Chiusura già presa da un'altra istanza closureId={}", id);
            return;
        }

        // un job non ha una richiesta HTTP: il correlation id lo crea qui, e arriva allo stub come header
        CorrelationId.set(CorrelationId.newId());
        try {
            log.info("Trasmissione closureId={} transmissionId={} tentativo={}",
                    id, closure.getTransmissionId(), closure.getAttempts());

            // 2. invio, fuori da qualsiasi transazione
            TransmissionOutcome outcome = client.send(closure);

            // 3. esito
            tx.executeWithoutResult(status -> applyOutcome(id, leaseUntil, outcome));
        } finally {
            CorrelationId.clear();
        }
    }

    private void applyOutcome(Long id, Instant leaseUntil, TransmissionOutcome outcome) {
        DailyClosure closure = closures.findById(id).orElseThrow();

        // se il mio lease è scaduto, un'altra istanza può aver ripreso la chiusura: non sovrascrivo il suo stato.
        // Lo stesso transmissionId garantisce comunque che l'ente non registri due volte.
        if (closure.getStatus() != ClosureStatus.IN_PROGRESS || !leaseUntil.equals(closure.getLeaseUntil())) {
            log.warn("Lease perso, esito ignorato closureId={} outcome={}", id, outcome);
            return;
        }

        Instant now = clock.instant();
        int maxAttempts = properties.transmission().maxAttempts();
        switch (outcome) {
            case Accepted accepted -> {
                closure.markTransmitted(accepted.externalRef(), now);
                log.info("Chiusura trasmessa closureId={} externalRef={}", id, accepted.externalRef());
            }
            case TransientFailure failure when closure.getAttempts() < maxAttempts -> {
                Instant nextAttemptAt = now.plus(backoff(closure.getAttempts()));
                closure.markRetry(failure.reason(), nextAttemptAt, now);
                log.warn("Trasmissione fallita, nuovo tentativo alle {} closureId={} tentativo={}/{}: {}",
                        nextAttemptAt, id, closure.getAttempts(), maxAttempts, failure.reason());
            }
            case TransientFailure failure -> {
                closure.markFailed("Tentativi esauriti (" + maxAttempts + "): " + failure.reason(), now);
                log.error("Chiusura FALLITA, tentativi esauriti closureId={}: {}", id, failure.reason());
            }
            case PermanentFailure failure -> {
                closure.markFailed(failure.reason(), now);
                log.error("Chiusura FALLITA, errore permanente closureId={}: {}", id, failure.reason());
            }
        }
    }

    /**
     * Backoff esponenziale: initialBackoff * 2^(attempts-1), con tetto maxBackoff, più un jitter casuale
     * fino al 20%. Il jitter evita che tante chiusure fallite insieme riprovino tutte nello stesso istante.
     */
    private Duration backoff(int attempts) {
        var transmission = properties.transmission();
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);   // evita overflow dello shift
        Duration delay = transmission.initialBackoff().multipliedBy(1L << exponent);
        if (delay.compareTo(transmission.maxBackoff()) > 0) {
            delay = transmission.maxBackoff();
        }
        long jitterMillis = ThreadLocalRandom.current().nextLong(delay.toMillis() / 5 + 1);
        return delay.plusMillis(jitterMillis);
    }
}
