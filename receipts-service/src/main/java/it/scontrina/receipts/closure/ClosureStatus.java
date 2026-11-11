package it.scontrina.receipts.closure;

/**
 * Macchina a stati della trasmissione:
 *
 *   TO_TRANSMIT --(job la prende in carico)--> IN_PROGRESS --(ok)--> TRANSMITTED
 *        ^                                         |
 *        +---(errore transitorio, attempts < max)--+--(attempts == max o errore permanente)--> FAILED
 *
 *   IN_PROGRESS con lease scaduto (istanza morta) torna prendibile dal job.
 */
public enum ClosureStatus {
    TO_TRANSMIT,
    IN_PROGRESS,
    TRANSMITTED,
    FAILED
}
