package it.scontrina.receipts.access;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementViewRepository extends JpaRepository<EntitlementView, EntitlementView.Key> {
}
