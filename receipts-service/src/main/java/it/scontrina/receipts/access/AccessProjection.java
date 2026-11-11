package it.scontrina.receipts.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applica gli eventi di Account alla copia locale.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessProjection {

    private final OrganizationViewRepository organizations;
    private final EntitlementViewRepository entitlements;

    @Transactional
    public void apply(AccountEvents.OrganizationEvent event) {
        Optional<OrganizationView> organizationView = organizations.findById(event.organizationId());
        if (organizationView.isEmpty()) {
            OrganizationView newOrgView = new OrganizationView(event.organizationId(), event.status(), event.version());
            organizations.saveAndFlush(newOrgView);
        } else if (event.version() > organizationView.get().getSourceVersion()) {
            organizationView.get().update(event.status(), event.version());
        } else {
            log.debug("Evento organizzazione ignorato orgId={} version={} <= sourceVersion={}",
                    event.organizationId(), event.version(), organizationView.get().getSourceVersion());
        }
    }

    @Transactional
    public void apply(AccountEvents.EntitlementChangedEvent event) {
        EntitlementView.Key key = new EntitlementView.Key(event.organizationId(), event.product());
        Optional<EntitlementView> view = entitlements.findById(key);
        if (view.isEmpty()) {
            EntitlementView newEntView = new EntitlementView(key, event.status(), event.validFrom(), event.validTo(), event.version());
            entitlements.saveAndFlush(newEntView);
        } else if (event.version() > view.get().getSourceVersion()) {
            view.get().update(event.status(), event.validFrom(), event.validTo(), event.version());
        } else {
            log.debug("Evento abilitazione ignorato orgId={} product={} version={} <= sourceVersion={}",
                    event.organizationId(), event.product(), event.version(), view.get().getSourceVersion());
        }
    }
}
