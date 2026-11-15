package it.scontrina.receipts.access;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.scontrina.receipts.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consuma gli eventi di Account e aggiorna la copia locale.
 * Un consumer group senza offset salvati legge dall'inizio del topic (auto-offset-reset: earliest)
 * e ricostruisce la copia da zero; un'istanza in più dello stesso group riparte invece dagli offset salvati.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventsListener {

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
