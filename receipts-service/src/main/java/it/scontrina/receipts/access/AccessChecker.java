package it.scontrina.receipts.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Verifica, sulla COPIA LOCALE, che un'organizzazione possa emettere.
 * <p>
 * Scelta: copia locale alimentata da eventi invece di chiamata sincrona ad Account.
 * + l'emissione funziona anche se Account è giù (la cassa non si ferma)
 * + nessuna latenza di rete aggiuntiva sul percorso critico
 * - consistenza eventuale: una sospensione arriva dopo qualche istante (finestra = lag del consumer)
 * - un'organizzazione appena registrata non può emettere finché l'evento non è arrivato
 */
@Component
@RequiredArgsConstructor
public class AccessChecker {

    public static final String PRODUCT_SCONTRINA = "SCONTRINA";

    private final OrganizationViewRepository organizations;
    private final EntitlementViewRepository entitlements;

    /**
     * @throws OrganizationNotEnabledException con un motivo esplicito
     * (organizzazione sconosciuta / sospesa / non abilitata / abilitazione scaduta o non ancora valida)
     */
    @Transactional(readOnly = true)
    public void ensureCanIssue(String organizationId, LocalDate businessDate) {
        Optional<OrganizationView> orgView = organizations.findById(organizationId);
        if (orgView.isEmpty()) {
            throw new OrganizationNotEnabledException("ORGANIZATION_UNKNOWN", "Organizzazione non trovata");
        } else if (!orgView.get().isActive()) {
            throw new OrganizationNotEnabledException("ORGANIZATION_SUSPENDED", "Organizzazione non attiva");
        }

        Optional<EntitlementView> entView = entitlements.findById(new EntitlementView.Key(organizationId, PRODUCT_SCONTRINA));
        if (entView.isEmpty()) {
            throw new OrganizationNotEnabledException("PRODUCT_NOT_ENABLED", "Prodotto 'SCONTRINA' non abilitato");
        } else if (!entView.get().isActiveOn(businessDate)) {
            throw new OrganizationNotEnabledException("ENTITLEMENT_NOT_ACTIVE", "Abilitazione SCONTRINA non attiva al " + businessDate);
        }
    }
}
