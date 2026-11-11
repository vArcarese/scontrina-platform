package it.scontrina.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Pubblica su Kafka gli eventi della outbox (pattern "polling publisher").
 *
 * Garanzia: at-least-once. Se l'invio riesce ma il commit del "published_at" fallisce,
 * l'evento verrà ripubblicato: per questo i consumer deduplicano per eventId.
 */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    public void publishPending() {
        // TODO: pubblicare su Kafka gli eventi in attesa, con conferma del broker
    }
}
