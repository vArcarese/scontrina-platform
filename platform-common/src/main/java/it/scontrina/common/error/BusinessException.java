package it.scontrina.common.error;

import org.springframework.http.HttpStatus;

/**
 * Eccezione applicativa con stato HTTP e codice errore stabile (leggibile dai client).
 * Ogni servizio crea le proprie sottoclassi, es. OrganizationNotEnabledException.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static BusinessException notFound(String what, Object id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " non trovato");
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException unprocessable(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
