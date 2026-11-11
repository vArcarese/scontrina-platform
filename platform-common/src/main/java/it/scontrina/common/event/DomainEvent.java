package it.scontrina.common.event;

import java.time.Instant;

/**
 * Contratto minimo di ogni evento pubblicato su Kafka.
 * eventId serve ai consumer per la deduplica (la consegna Kafka + outbox è at-least-once).
 */
public interface DomainEvent {

    String eventId();

    String eventType();

    Instant occurredAt();
}
