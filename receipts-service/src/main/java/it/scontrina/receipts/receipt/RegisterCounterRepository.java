package it.scontrina.receipts.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisterCounterRepository extends JpaRepository<RegisterCounter, RegisterCounter.Key> {

    // TODO: lettura del contatore con lock fino a fine transazione (anche alla prima emissione su una cassa nuova)
}
