package it.scontrina.receipts.closure;

import it.scontrina.receipts.config.ReceiptsProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Job di trasmissione delle chiusure. Può girare su più istanze contemporaneamente:
 * la correttezza NON dipende dal fatto che giri una volta sola, ma dal claim atomico a DB.
 */
@Component
@RequiredArgsConstructor
public class TransmissionJob {

    private static final Logger log = LoggerFactory.getLogger(TransmissionJob.class);

    private final DailyClosureRepository closures;
    private final TransmissionClient client;
    private final ReceiptsProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${receipts.transmission.poll-interval}")
    public void run() {
        // TODO: presa in carico, invio all'ente esterno, aggiornamento dello stato con retry e backoff
    }
}
