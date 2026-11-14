package it.scontrina.receipts.closure;

import it.scontrina.receipts.config.ReceiptsProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Client HTTP verso l'ente esterno (stub). Traduce l'esito tecnico in un esito di dominio:
 *  - ok                                -> Accepted
 *  - timeout / connessione / 5xx / 429 -> TransientFailure (si riprova)
 *  - altri 4xx                         -> PermanentFailure (riprovare non serve)
 *
 * Attenzione al timeout: NON significa "non ricevuto". L'ente potrebbe aver registrato la trasmissione
 * e la risposta essersi persa. Per questo mandiamo sempre lo stesso transmissionId: l'ente deduplica.
 */
@Component
public class TransmissionClient {

    private final RestClient restClient;

    public TransmissionClient(RestClient.Builder builder, ReceiptsProperties properties) {
        var transmission = properties.transmission();
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(transmission.connectTimeout());
        requestFactory.setReadTimeout(transmission.readTimeout());
        this.restClient = builder
                .baseUrl(transmission.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public TransmissionOutcome send(DailyClosure closure) {
        var request = new TransmissionRequest(closure.getTransmissionId(), closure.getOrganizationId(),
                closure.getRegisterId(), closure.getBusinessDate(), closure.getReceiptCount(),
                closure.getFirstNumber(), closure.getLastNumber(), closure.getTotal());
        try {
            TransmissionResponse response = restClient.post()
                    .uri("/api/transmissions")
                    .body(request)
                    .retrieve()
                    .body(TransmissionResponse.class);
            return new TransmissionOutcome.Accepted(response != null ? response.externalRef() : null);
        } catch (ResourceAccessException e) {
            return new TransmissionOutcome.TransientFailure("I/O o timeout: " + e.getMessage());
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String detail = "HTTP " + status.value() + ": " + e.getResponseBodyAsString();
            if (status.is5xxServerError() || status.value() == 429) {
                return new TransmissionOutcome.TransientFailure(detail);
            }
            return new TransmissionOutcome.PermanentFailure(detail);
        } catch (RestClientException e) {
            // es. read timeout scattato mentre RestClient legge gli header della risposta: arriva come
            // RestClientException generica (non ResourceAccessException). L'ente può aver registrato: si riprova
            // con lo stesso transmissionId.
            return new TransmissionOutcome.TransientFailure("Errore di comunicazione: " + e.getMessage());
        }
    }

    record TransmissionRequest(String transmissionId, String organizationId, String registerId,
                               LocalDate businessDate, int receiptCount, Long firstNumber,
                               Long lastNumber, BigDecimal total) {
    }

    record TransmissionResponse(String transmissionId, String externalRef, String status) {
    }

    /** Esito chiuso (sealed): il chiamante con uno switch deve gestire tutti i casi. */
    public sealed interface TransmissionOutcome {
        record Accepted(String externalRef) implements TransmissionOutcome {
        }

        record TransientFailure(String reason) implements TransmissionOutcome {
        }

        record PermanentFailure(String reason) implements TransmissionOutcome {
        }
    }
}
