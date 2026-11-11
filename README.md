# Scontrina Platform

Sistema a microservizi per l'emissione di documenti commerciali (scontrini) per conto di organizzazioni
clienti, con verifica delle abilitazioni e trasmissione delle chiusure giornaliere a un ente esterno simulato.

> Stato: **in sviluppo**.

## Avvio

Prerequisiti: Docker (con Docker Compose v2). Per build e test in locale: JDK 21 e Maven 3.9+.

```bash
docker compose up --build
```

| Componente | URL |
|---|---|
| account-service | http://localhost:8081 — Swagger UI: http://localhost:8081/swagger-ui.html |
| receipts-service | http://localhost:8082 — Swagger UI: http://localhost:8082/swagger-ui.html |
| transmission-stub | http://localhost:8090 |
| Kafka UI | http://localhost:8080 |

## Test

```bash
mvn test      # test unitari
mvn verify    # unitari + integrazione (Testcontainers: serve Docker attivo)
```
