package it.scontrina.outbox;

import it.scontrina.common.correlation.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pubblica su Kafka gli eventi della outbox (pattern "polling publisher").
 * <p>
 * Garanzia: at-least-once. Se l'invio riesce ma il commit del "published_at" fallisce,
 * l'evento verrà ripubblicato: per questo i consumer sono idempotenti (scartano gli eventi con version non più recente).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = repository.findPendingByLimit(batchSize);
        for (OutboxEvent event : events) {
            ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
            if (event.getCorrelationId() != null) {
                record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            }
            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished(clock.instant());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | KafkaException e) {
                log.warn("Pubblicazione fallita eventId={} topic={} key={}: {}",
                        event.getId(), event.getTopic(), event.getMessageKey(), e.getMessage());
                break;
            }
        }
    }
}
