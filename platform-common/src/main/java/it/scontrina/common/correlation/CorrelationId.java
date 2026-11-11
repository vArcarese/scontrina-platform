package it.scontrina.common.correlation;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Identificativo di correlazione propagato tra i servizi:
 * header HTTP {@value #HEADER}, header Kafka con lo stesso nome, chiave MDC {@value #MDC_KEY}
 * (finisce in automatico in ogni riga di log JSON).
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String id = current();
        return id != null ? id : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
