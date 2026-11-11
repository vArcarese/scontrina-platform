package it.scontrina.receipts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * @param businessZone fuso orario con cui si determina la "giornata" di un documento (chiusura giornaliera)
 * @param transmission parametri di trasmissione verso l'ente esterno
 */
@ConfigurationProperties(prefix = "receipts")
public record ReceiptsProperties(ZoneId businessZone, Transmission transmission) {

    /**
     * @param baseUrl         URL dello stub dell'ente esterno
     * @param connectTimeout  timeout di connessione
     * @param readTimeout     timeout di lettura della risposta (lo stub a volte è lento)
     * @param maxAttempts     tentativi massimi prima di FAILED definitivo
     * @param initialBackoff  attesa dopo il primo fallimento (poi esponenziale)
     * @param maxBackoff      tetto dell'attesa
     * @param lease           durata della "presa in carico" di una chiusura da parte di un'istanza
     * @param pollInterval    frequenza del job di trasmissione
     */
    public record Transmission(String baseUrl,
                               Duration connectTimeout,
                               Duration readTimeout,
                               int maxAttempts,
                               Duration initialBackoff,
                               Duration maxBackoff,
                               Duration lease,
                               Duration pollInterval) {
    }
}
