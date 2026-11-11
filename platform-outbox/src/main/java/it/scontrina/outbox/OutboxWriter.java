package it.scontrina.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.scontrina.common.correlation.CorrelationId;
import it.scontrina.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Accoda un evento nella tabella outbox.
 *
 * Propagation.MANDATORY: se chiamato fuori da una transazione lancia eccezione.
 * È voluto: l'evento DEVE essere salvato nella stessa transazione del cambiamento di stato,
 * altrimenti si torna al problema del "dual write" (DB aggiornato ma evento perso, o viceversa).
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String messageKey, DomainEvent event) {
        repository.save(new OutboxEvent(
                event.eventId(),
                topic,
                messageKey,
                event.eventType(),
                toJson(event),
                CorrelationId.current(),
                clock.instant()));
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serializzazione evento fallita: " + event.eventType(), e);
        }
    }
}
