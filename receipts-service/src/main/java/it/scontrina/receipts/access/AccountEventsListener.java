package it.scontrina.receipts.access;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.scontrina.receipts.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consuma gli eventi di Account e aggiorna la copia locale.
 * Il consumer group legge dall'inizio del topic (auto-offset-reset: earliest),
 * quindi un'istanza nuova ricostruisce la copia da zero.
 */
@Component
@RequiredArgsConstructor
public class AccountEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEventsListener.class);

    private final ObjectMapper objectMapper;
    private final AccessProjection projection;

    @KafkaListener(topics = KafkaTopics.ACCOUNT_ORGANIZATIONS, groupId = "receipts-access")
    public void onOrganizationEvent(String payload) throws JsonProcessingException {
        var event = objectMapper.readValue(payload, AccountEvents.OrganizationEvent.class);
        log.info("Evento organizzazione ricevuto type={} org={} version={}",
                event.eventType(), event.organizationId(), event.version());
        projection.apply(event);
    }

    @KafkaListener(topics = KafkaTopics.ACCOUNT_ENTITLEMENTS, groupId = "receipts-access")
    public void onEntitlementEvent(String payload) throws JsonProcessingException {
        var event = objectMapper.readValue(payload, AccountEvents.EntitlementChangedEvent.class);
        log.info("Evento abilitazione ricevuto org={} product={} status={} version={}",
                event.organizationId(), event.product(), event.status(), event.version());
        projection.apply(event);
    }
}
