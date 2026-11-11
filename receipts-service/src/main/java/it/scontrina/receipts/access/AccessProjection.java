package it.scontrina.receipts.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applica gli eventi di Account alla copia locale. */
@Component
@RequiredArgsConstructor
public class AccessProjection {

    private final OrganizationViewRepository organizations;
    private final EntitlementViewRepository entitlements;

    @Transactional
    public void apply(AccountEvents.OrganizationEvent event) {
        // TODO: upsert di OrganizationView, ignorando eventi duplicati o fuori ordine (version)
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional
    public void apply(AccountEvents.EntitlementChangedEvent event) {
        // TODO: upsert di EntitlementView, stessa logica
        throw new UnsupportedOperationException("TODO");
    }
}
