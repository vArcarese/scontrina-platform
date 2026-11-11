package it.scontrina.account.entitlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * PUT perché il provisioning è idempotente per definizione:
 * il client dichiara lo stato desiderato, ripetere la chiamata produce lo stesso risultato.
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/entitlements")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService service;

    @PutMapping("/{product}")
    public EntitlementResponse provision(@PathVariable String organizationId,
                                         @PathVariable Product product,
                                         @Valid @RequestBody ProvisionRequest request) {
        return EntitlementResponse.from(service.provision(organizationId, product,
                request.status(), request.validFrom(), request.validTo()));
    }

    @GetMapping
    public List<EntitlementResponse> list(@PathVariable String organizationId) {
        return service.list(organizationId).stream().map(EntitlementResponse::from).toList();
    }

    public record ProvisionRequest(@NotNull EntitlementStatus status,
                                   @NotNull LocalDate validFrom,
                                   LocalDate validTo) {
    }

    public record EntitlementResponse(String organizationId, Product product, EntitlementStatus status,
                                      LocalDate validFrom, LocalDate validTo, long version) {
        static EntitlementResponse from(Entitlement e) {
            return new EntitlementResponse(e.getOrganizationId(), e.getProduct(), e.getStatus(),
                    e.getValidFrom(), e.getValidTo(), e.getVersion());
        }
    }
}
