package it.scontrina.receipts.access;

import it.scontrina.common.error.BusinessException;
import org.springframework.http.HttpStatus;

/** 403: la richiesta è valida, ma l'organizzazione non ha il diritto di emettere. */
public class OrganizationNotEnabledException extends BusinessException {

    public OrganizationNotEnabledException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
