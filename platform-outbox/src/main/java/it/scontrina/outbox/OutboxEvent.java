package it.scontrina.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Riga della tabella outbox_event (la DDL è nelle migration Flyway di ogni servizio:
 * ogni servizio ha la SUA tabella nel SUO database).
 */
@Entity
@Table(name = "outbox_event")
@Getter
public class OutboxEvent implements Persistable<String> {

    /** Coincide con l'eventId dell'evento: chiave di deduplica per i consumer. */
    @Id
    private String id;

    /** Id assegnato da noi: senza questo flag save() farebbe merge (SELECT + INSERT). */
    @Transient
    @Getter(AccessLevel.NONE)   // solo per Persistable.isNew(), non una proprietà da esporre
    private boolean isNew = true;

    @Column(nullable = false)
    private String topic;

    /** Chiave del messaggio Kafka: determina la partizione e quindi l'ordinamento. */
    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String id, String topic, String messageKey, String eventType,
                       String payload, String correlationId, Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
