package it.scontrina.account.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntitlementRepository extends JpaRepository<Entitlement, Long> {

    Optional<Entitlement> findByOrganizationIdAndProduct(String organizationId, Product product);

    List<Entitlement> findByOrganizationId(String organizationId);
}
