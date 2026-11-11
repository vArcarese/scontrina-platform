package it.scontrina.receipts.closure;

import it.scontrina.common.error.BusinessException;
import it.scontrina.receipts.receipt.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClosureService {

    private final DailyClosureRepository closures;
    private final ReceiptRepository receipts;
    private final Clock clock;

    /**
     * Chiude la giornata di un punto cassa: raggruppa i documenti del giorno e crea la chiusura
     * in stato TO_TRANSMIT. La trasmissione vera la fa TransmissionJob, in modo asincrono.
     */
    @Transactional
    public ClosureResult close(String organizationId, String registerId, LocalDate businessDate) {
        // TODO: creare la chiusura del giorno in modo idempotente
        throw new UnsupportedOperationException("TODO");
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
}
