package it.scontrina.stub;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param failureRate probabilità [0..1] di rispondere 503 senza registrare nulla
 * @param slowRate    probabilità [0..1] di rispondere in ritardo (DOPO aver registrato la trasmissione)
 * @param slowDelay   durata del ritardo
 */
@ConfigurationProperties(prefix = "stub")
public record StubProperties(double failureRate, double slowRate, Duration slowDelay) {
}
