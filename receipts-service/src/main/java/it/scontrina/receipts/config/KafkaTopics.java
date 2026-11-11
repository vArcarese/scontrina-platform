package it.scontrina.receipts.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    /** Pubblicato da Receipts: documento emesso / annullato. Chiave = organizationId. */
    public static final String DOCUMENTS = "receipts.documents";

    /** Consumati da Receipts per la copia locale delle abilitazioni (di proprietà di Account). */
    public static final String ACCOUNT_ORGANIZATIONS = "account.organizations";
    public static final String ACCOUNT_ENTITLEMENTS = "account.entitlements";

    @Bean
    NewTopic documentsTopic() {
        return TopicBuilder.name(DOCUMENTS).partitions(3).replicas(1).build();
    }
}
