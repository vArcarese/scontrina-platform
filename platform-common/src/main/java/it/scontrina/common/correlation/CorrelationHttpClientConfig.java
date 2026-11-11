package it.scontrina.common.correlation;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ogni RestClient creato dal RestClient.Builder di Spring propaga il correlation id corrente.
 */
@Configuration(proxyBeanMethods = false)
public class CorrelationHttpClientConfig {

    @Bean
    RestClientCustomizer correlationIdRestClientCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String id = CorrelationId.current();
            if (id != null) {
                request.getHeaders().set(CorrelationId.HEADER, id);
            }
            return execution.execute(request, body);
        });
    }
}
