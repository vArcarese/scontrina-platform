package it.scontrina.receipts.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Verifica, sulla COPIA LOCALE, che un'organizzazione possa emettere.
 *
 * Scelta: copia locale alimentata da eventi invece di chiamata sincrona ad Account.
 *  + l'emissione funziona anche se Account è giù (la cassa non si ferma)
 *  + nessuna latenza di rete aggiuntiva sul percorso critico
 *  - consistenza eventuale: una sospensione arriva dopo qualche istante (finestra = lag del consumer)
 *  - un'organizzazione appena registrata non può emettere finché l'evento non è arrivato
 */
@Component
@RequiredArgsConstructor
public class AccessChecker {

    public static final String PRODUCT_SCONTRINA = "SCONTRINA";

    private final OrganizationViewRepository organizations;
    private final EntitlementViewRepository entitlements;

    /**
     * @throws OrganizationNotEnabledException con un motivo esplicito
     *         (organizzazione sconosciuta / sospesa / non abilitata / abilitazione scaduta o non ancora valida)
     */
    public void ensureCanIssue(String organizationId, LocalDate businessDate) {
        // TODO: verificare organizzazione attiva e abilitazione SCONTRINA valida alla businessDate
        throw new UnsupportedOperationException("TODO");
    }
}
