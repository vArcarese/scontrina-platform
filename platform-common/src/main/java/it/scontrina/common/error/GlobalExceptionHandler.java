package it.scontrina.common.error;

import it.scontrina.common.correlation.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Formato di errore uniforme per tutte le API: RFC 9457 (ex 7807) "Problem Details",
 * esteso con: code (stabile, per i client), correlationId, timestamp ed eventuali errori di validazione.
 *
 * <pre>
 * {
 *   "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
 *   "detail": "Organizzazione non abilitata a SCONTRINA",
 *   "code": "ORGANIZATION_NOT_ENABLED", "correlationId": "...", "timestamp": "..."
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex) {
        log.info("Errore applicativo code={} detail={}", ex.code(), ex.getMessage());
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Risorsa duplicata detail={}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "La risorsa esiste già (creata da un'altra richiesta concorrente)");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("Conflitto di concorrenza detail={}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "La risorsa è stata modificata da un'altra richiesta, rileggila e riprova");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Errore inatteso", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Errore interno");
    }

    /** Errori di Bean Validation (@Valid sul body). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Richiesta non valida");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Arricchisce anche i ProblemDetail generati da Spring (404 route, 405, body illeggibile...). */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getProperties() == null) {
            enrich(pd, "HTTP_" + statusCode.value());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, code);
        return pd;
    }

    private static void enrich(ProblemDetail pd, String code) {
        pd.setProperty("code", code);
        pd.setProperty("correlationId", CorrelationId.current());
        pd.setProperty("timestamp", Instant.now());
    }
}
