package it.scontrina.common.correlation;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Lato consumer Kafka: prima di ogni record mette in MDC il correlation id letto dall'header,
 * così i log del listener sono collegati alla richiesta HTTP che ha originato l'evento.
 * Spring Boot applica in automatico un bean RecordInterceptor alla container factory dei listener.
 */
@Component
@ConditionalOnClass(name = "org.springframework.kafka.listener.RecordInterceptor")
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String id = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : CorrelationId.newId();
        CorrelationId.set(id);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        CorrelationId.clear();
    }
}
