package it.scontrina.account.config;

import it.scontrina.account.entitlement.Product;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic di proprietà di Account.
 *
 * Entrambi COMPATTATI (cleanup.policy=compact): Kafka non cancella per età, ma conserva almeno
 * l'ultimo messaggio per ogni chiave. Così i consumer (es. la copia locale in Receipts) possono sempre
 * ricostruire lo stato rileggendo il topic dall'inizio. Funziona perché gli eventi trasportano
 * lo STATO COMPLETO dell'entità, non un delta.
 *
 * La chiave identifica l'entità, e determina partizione e ordinamento:
 *  - account.organizations -> organizationId
 *  - account.entitlements  -> organizationId:product  (con il solo organizationId la compaction
 *                             terrebbe solo l'ultima abilitazione dell'organizzazione, perdendo le altre)
 */
@Configuration
public class KafkaTopics {

    public static final String ORGANIZATIONS = "account.organizations";
    public static final String ENTITLEMENTS = "account.entitlements";

    /** Chiave Kafka di un'abilitazione: una per coppia (organizzazione, prodotto). */
    public static String entitlementKey(String organizationId, Product product) {
        return organizationId + ":" + product.name();
    }

    @Bean
    NewTopic organizationsTopic() {
        return TopicBuilder.name(ORGANIZATIONS).partitions(3).replicas(1).compact().build();
    }

    @Bean
    NewTopic entitlementsTopic() {
        return TopicBuilder.name(ENTITLEMENTS).partitions(3).replicas(1).compact().build();
    }
}
