# Scontrina Platform

Sistema a microservizi per l'emissione di documenti commerciali (scontrini) per conto di organizzazioni
clienti: verifica che l'organizzazione sia abilitata, numera i documenti per punto cassa, calcola totali e IVA,
e trasmette le chiusure giornaliere a un ente esterno simulato con retry e senza doppie trasmissioni.

**Cosa è implementato:** ambito Account, ambito Receipts (emissione, annullamento, chiusure, trasmissione).
**Cosa non è implementato:** il servizio CRM (facoltativo), vedi [Semplificazioni](#semplificazioni-e-parti-non-implementate).

---

## Indice

1. [Avvio](#avvio)
2. [Provare le API](#provare-le-api)
3. [Test](#test)
4. [Architettura](#architettura)
5. [Scelte progettuali](#scelte-progettuali)
6. [Punti critici](#punti-critici)
7. [Semplificazioni e parti non implementate](#semplificazioni-e-parti-non-implementate)
8. [Limiti noti](#limiti-noti)
9. [Cosa farei con più tempo](#cosa-farei-con-più-tempo)
10. [Uso dell'intelligenza artificiale](#uso-dellintelligenza-artificiale)

---

## Avvio

Prerequisiti: Docker con Docker Compose v2. Per build e test in locale: JDK 21 e Maven 3.9+.

```bash
docker compose up --build
```

| Componente | URL |
|---|---|
| account-service | http://localhost:8081 — Swagger UI: http://localhost:8081/swagger-ui.html |
| receipts-service | http://localhost:8082 — Swagger UI: http://localhost:8082/swagger-ui.html |
| transmission-stub | http://localhost:8090 |
| Kafka UI | http://localhost:8080 |

Lo stub dell'ente esterno è configurabile con variabili d'ambiente: `STUB_FAILURE_RATE` (probabilità di 503,
default 0.3), `STUB_SLOW_RATE` (probabilità di risposta oltre il timeout del client, default 0.2),
`STUB_SLOW_DELAY` (default 5s).

## Provare le API

Con Swagger UI oppure qui sotto lo scenario completo da riga di comando, in due varianti:
[PowerShell](#powershell) e [curl](#curl).

### PowerShell

```powershell
$account  = 'http://localhost:8081/api/organizations'
$json     = 'application/json; charset=utf-8'

# 1. Account: registra un'organizzazione (partita IVA casuale, così lo scenario si può rilanciare) e abilitala
$vat = -join (1..11 | ForEach-Object { Get-Random -Maximum 10 })
$org = Invoke-RestMethod -Method Post -Uri $account -ContentType $json `
       -Body "{`"vatNumber`":`"$vat`",`"legalName`":`"Bar Prova`"}"

Invoke-RestMethod -Method Put -Uri "$account/$($org.id)/entitlements/SCONTRINA" -ContentType $json `
    -Body '{"status":"ENABLED","validFrom":"2026-01-01"}'

$receipts = "http://localhost:8082/api/organizations/$($org.id)"

# 2. Receipts: emetto un documento (l'abilitazione arriva a Receipts via Kafka in pochi istanti)
Start-Sleep -Seconds 2
$body = '{"registerId":"CASSA-1","paymentType":"CARD","lines":[
           {"description":"Caffe","quantity":2,"unitPrice":1.20,"vatRate":10},
           {"description":"Acqua","quantity":1,"unitPrice":1.00,"vatRate":22}]}'
$receipt = Invoke-RestMethod -Method Post -Uri "$receipts/receipts" -ContentType $json `
           -Headers @{ 'Idempotency-Key' = 'chiave-1' } -Body $body
$receipt | ConvertTo-Json -Depth 5
# ripetendo lo stesso comando con la stessa chiave si ottiene lo stesso documento

# 3. Annullamento (nuovo documento VOID collegato all'originale)
Invoke-RestMethod -Method Post -Uri "$receipts/receipts/$($receipt.id)/void" -ContentType $json `
    -Headers @{ 'Idempotency-Key' = 'annullo-1' } -Body '{"registerId":"CASSA-1"}'

# 4. Chiusura giornaliera (202: la trasmissione avviene in modo asincrono) ed esito dopo qualche secondo
$today = Get-Date -Format 'yyyy-MM-dd'
$closure = Invoke-RestMethod -Method Post -Uri "$receipts/registers/CASSA-1/closures" -ContentType $json `
           -Body "{`"businessDate`":`"$today`"}"
Start-Sleep -Seconds 15
Invoke-RestMethod -Uri "$receipts/closures/$($closure.id)"

# 5. Lato ente: quante volte ha ricevuto la trasmissione (registrata comunque una sola volta)
Invoke-RestMethod -Uri "http://localhost:8090/api/transmissions/$($closure.transmissionId)"
```

### curl

```bash
# 1. Account: registra un'organizzazione e abilitala a SCONTRINA
curl -s -X POST localhost:8081/api/organizations -H 'Content-Type: application/json' \
     -d '{"vatNumber":"12345678901","legalName":"Bar Prova"}'
ORG=<id restituito>

curl -s -X PUT localhost:8081/api/organizations/$ORG/entitlements/SCONTRINA -H 'Content-Type: application/json' \
     -d '{"status":"ENABLED","validFrom":"2026-01-01"}'

# 2. Receipts: emetto un documento (l'abilitazione arriva a Receipts via Kafka in pochi istanti)
curl -s -X POST localhost:8082/api/organizations/$ORG/receipts -H 'Content-Type: application/json' \
     -H 'Idempotency-Key: chiave-1' \
     -d '{"registerId":"CASSA-1","paymentType":"CARD","lines":[
           {"description":"Caffè","quantity":2,"unitPrice":1.20,"vatRate":10},
           {"description":"Acqua","quantity":1,"unitPrice":1.00,"vatRate":22}]}'
# ripetendo la stessa richiesta con la stessa chiave: 200 e lo stesso documento

# 3. Annullamento (nuovo documento VOID collegato all'originale)
curl -s -X POST localhost:8082/api/organizations/$ORG/receipts/<receiptId>/void -H 'Content-Type: application/json' \
     -H 'Idempotency-Key: annullo-1' -d '{"registerId":"CASSA-1"}'

# 4. Chiusura giornaliera (202: la trasmissione avviene in modo asincrono) ed esito
curl -s -X POST localhost:8082/api/organizations/$ORG/registers/CASSA-1/closures -H 'Content-Type: application/json' \
     -d '{"businessDate":"<oggi, yyyy-MM-dd>"}'
curl -s localhost:8082/api/organizations/$ORG/closures

# 5. Lato ente: quante volte ha ricevuto una trasmissione (registrata comunque una sola volta)
curl -s localhost:8090/api/transmissions/<transmissionId>
```

Ogni chiamata accetta un header `X-Correlation-Id` (se manca viene generato) che si ritrova nei log di tutti i
servizi coinvolti, negli eventi Kafka e nelle risposte di errore.

## Test

```bash
mvn test      # test unitari
mvn verify    # unitari + integrazione (Testcontainers con MySQL e Kafka reali: serve Docker attivo)
```

| Test | Tipo | Cosa dimostra |
|---|---|---|
| `VatCalculatorTest` | unitario | regola di arrotondamento: riga singola, più aliquote (ordine del riepilogo), scontrino vuoto |
| `ReceiptConcurrencyIT` | integrazione | **20 emissioni concorrenti sulla stessa cassa → numeri esattamente 1..20**; **20 richieste concorrenti con la stessa Idempotency-Key → un solo documento**, un solo numero consumato |
| `ClosureClaimConcurrencyIT` | integrazione | **10 claim concorrenti sulla stessa chiusura → uno solo riesce**, la chiusura passa a `IN_PROGRESS` con un solo tentativo conteggiato (nessuna doppia trasmissione anche con più istanze) |
| `AccountApplicationIT` | integrazione | **provisioning ripetuto identico → una sola abilitazione e un solo evento** in outbox, nessuna modifica (version invariata); **provisioning con validità diversa → abilitazione aggiornata e nuovo evento** |
| `ReceiptsApplicationIT` | integrazione | il contesto parte su MySQL e Kafka reali e le migration Flyway si applicano |

Il test sulla numerazione è stato verificato anche "al contrario": togliendo il lock pessimistico dal contatore,
il test fallisce.

---

## Architettura

```
                 HTTP                                   HTTP (timeout, retry)
   client ───────────────► account-service              receipts-service ─────────────► transmission-stub
                               │  MySQL account_db          │  MySQL receipts_db            (ente esterno)
                               │  + outbox                  │  + outbox
                               ▼                            ▼
                        ┌─────────────────────── Kafka ─────────────────────────┐
                        │ account.organizations   account.entitlements           │
                        │ receipts.documents                                     │
                        └────────────────────────────────────────────────────────┘
                                         │
                                         └──► receipts-service: copia locale delle abilitazioni
```

| Modulo | Responsabilità | Storage |
|---|---|---|
| `account-service` | organizzazioni e abilitazioni ai prodotti; pubblica un evento per ogni cambiamento | MySQL `account_db` |
| `receipts-service` | emissione e annullamento dei documenti, numerazione, chiusure giornaliere, trasmissione; mantiene una copia locale delle abilitazioni | MySQL `receipts_db` |
| `transmission-stub` | ente esterno simulato: 503 casuali, risposte lente, deduplica per `transmissionId` | in memoria |
| `platform-common` | correlation id (filtro HTTP, interceptor Kafka e RestClient), formato degli errori, contratto degli eventi | — |
| `platform-outbox` | transactional outbox: scrittura dell'evento nella transazione del dato e relay verso Kafka | — |

Ogni servizio ha il proprio database e il proprio utente, senza permessi sul database dell'altro. I servizi
comunicano solo tramite Kafka (eventi) e HTTP (lo stub). Le librerie `platform-*` non contengono logica di dominio.

Dentro ogni servizio il codice è organizzato **per funzionalità** e non per layer: in Receipts i package sono
`receipt` (emissione), `closure` (chiusure e trasmissione) e `access` (copia locale delle abilitazioni).

---

## Scelte progettuali

### MySQL per le fonti di verità, MongoDB previsto per il CRM

Account e Receipts usano MySQL perché i requisiti critici si basano su garanzie che un database relazionale offre
nativamente: transazioni ACID, vincoli `UNIQUE`, lock di riga (`SELECT … FOR UPDATE`) e scrittura atomica di dato
ed evento (outbox).

MongoDB era destinato al CRM: lo storico acquisti per cliente è un *read model* costruito dagli eventi, un
documento per cliente, letto per chiave, senza relazioni né transazioni su più tabelle. Il CRM non è stato
implementato (vedi [Semplificazioni](#semplificazioni-e-parti-non-implementate)), quindi oggi MongoDB è presente in
`docker-compose.yml` ma non è usato da nessun servizio.

### Spring MVC con virtual thread

Ho scelto Spring MVC con i **virtual thread** di Java 21 (`spring.threads.virtual.enabled: true`):

- **Il servizio passa quasi tutto il tempo ad aspettare** database, Kafka e l'ente esterno. Con i virtual thread
  un'attesa non occupa un thread del sistema operativo, quindi il servizio regge molte richieste contemporanee.
- **Numerazione, idempotenza e outbox si basano su JPA, `@Transactional` e `SELECT … FOR UPDATE`.** Con i
  virtual thread restano utilizzabili così come sono.
- **Il codice resta sequenziale**, facile da leggere, da testare e da debuggare; il correlation id nei log
  funziona con il normale MDC.

### Controllo dell'abilitazione su una copia locale alimentata da eventi

Receipts consuma gli eventi di Account e mantiene `organization_view` ed `entitlement_view`. L'emissione controlla
la copia locale, non chiama Account.

- **Se Account non è raggiungibile, Receipts continua a emettere** sulla base dell'ultimo stato noto: le casse
  non si fermano, e non c'è latenza di rete sul percorso critico.
- Il prezzo è la **consistenza eventuale**: una sospensione diventa effettiva dopo il tempo di propagazione
  dell'evento (normalmente meno di un secondo), e un'organizzazione appena registrata o abilitata non può
  emettere finché l'evento non è arrivato (risponde 403 `ORGANIZATION_UNKNOWN` o `PRODUCT_NOT_ENABLED`).
- Alternativa scartata: chiedere ad Account, a ogni emissione, se l'organizzazione è abilitata. È sempre
  aggiornata, ma se Account non risponde bisogna scegliere tra bloccare le emissioni (le casse si fermano) o
  emettere senza controllo.

Gli eventi possono arrivare duplicati (consegna at-least-once) o fuori ordine. Ogni evento porta la `version`
dell'entità in Account; la copia locale applica un evento solo se `version > sourceVersion`, altrimenti lo ignora.
Questo rende la proiezione idempotente senza una tabella di eventi già processati: è possibile rileggere i topic
dall'inizio senza alterare i dati. I topic di Account sono **compattati** (chiave `organizationId`, e
`organizationId:product` per le abilitazioni): conservano almeno l'ultimo stato di ogni entità, quindi un nuovo
consumer group (un altro servizio, o Receipts dopo un reset degli offset) può ricostruire la copia locale
rileggendoli dall'inizio. Una nuova istanza dello stesso consumer group invece non rilegge nulla: riparte dagli
offset già salvati e usa la copia locale che è già nel database.

### Regola di arrotondamento

I prezzi unitari sono **IVA inclusa**, come sugli scontrini.

1. `lineTotal = quantity × unitPrice`, arrotondato a 2 decimali `HALF_UP`.
2. Le righe si sommano **per aliquota** (lordo dell'aliquota).
3. IVA scorporata sul lordo di ogni aliquota: `imponibile = lordo × 100 / (100 + aliquota)`, 2 decimali `HALF_UP`;
   `IVA = lordo − imponibile`.
4. Totale documento = somma dei lordi; IVA documento = somma delle IVA.

Lo scorporo sul totale dell'aliquota, e non riga per riga, evita che gli arrotondamenti si accumulino (tre righe
da 0,10 € al 22%: 0,06 € di IVA riga per riga, 0,05 € sul totale). Il calcolo per differenza garantisce che
`lordo = imponibile + IVA` al centesimo. Sempre `BigDecimal`, mai `double`.

### Errori

Tutti i servizi rispondono agli errori con lo stesso formato JSON, *Problem Details* (lo standard RFC 9457,
supportato nativamente da Spring con la classe `ProblemDetail`), gestito in un unico punto
(`GlobalExceptionHandler` in `platform-common`). Ai campi standard ho aggiunto `code` (un codice stabile su cui
il client può basare la sua logica), `correlationId` e `timestamp`:

```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "La chiave di idempotenza è già stata usata per una richiesta diversa",
  "instance": "/api/organizations/c4c3f24b-…/receipts",
  "code": "IDEMPOTENCY_KEY_REUSED",
  "correlationId": "398a7a9e-3f04-483c-b580-1bb393584281",
  "timestamp": "2026-11-14T11:10:29.715679Z"
}
```

| HTTP | `code` | Quando |
|---|---|---|
| 400 | `VALIDATION_ERROR` | campi mancanti, non validi o troppo lunghi (Bean Validation); `Idempotency-Key` vuota o oltre 64 caratteri |
| 403 | `ORGANIZATION_UNKNOWN`, `ORGANIZATION_SUSPENDED`, `PRODUCT_NOT_ENABLED`, `ENTITLEMENT_NOT_ACTIVE` | l'organizzazione non può emettere |
| 404 | `NOT_FOUND` | risorsa inesistente o di un'altra organizzazione |
| 409 | `ORGANIZATION_ALREADY_EXISTS` | partita IVA già registrata |
| 409 | `ALREADY_VOIDED` | documento già annullato |
| 409 | `DUPLICATE_RESOURCE`, `CONCURRENT_MODIFICATION` | conflitto tra richieste concorrenti (vincolo `UNIQUE` o lock ottimistico): la richiesta può essere ripetuta |
| 422 | `IDEMPOTENCY_KEY_REUSED` | stessa chiave di idempotenza con una richiesta diversa |
| 422 | `INVALID_VALIDITY_PERIOD`, `RECEIPT_NOT_VOIDABLE` | richiesta non valida per le regole di dominio |

### Log e tracciabilità

Log in formato JSON (ECS) quando i servizi girano in Docker; in locale un formato leggibile con il correlation id
nel pattern. Il correlation id viaggia come header HTTP `X-Correlation-Id`, come header dei messaggi Kafka (salvato
nell'outbox insieme all'evento) e nelle risposte di errore. Il job di trasmissione, che non nasce da una richiesta
HTTP, ne genera uno per ogni chiusura: i log di Receipts e dello stub per la stessa trasmissione sono collegati.

---

## Punti critici

### Numerazione concorrente

Requisito: numerazione progressiva per punto cassa, senza buchi né duplicati, anche con richieste concorrenti.

- Una riga `register_counter` per (organizzazione, punto cassa) con l'ultimo numero emesso.
- In emissione: `INSERT … ON DUPLICATE KEY UPDATE` crea la riga se manca (prima emissione su una cassa nuova,
  dove non ci sarebbe niente da bloccare), poi `SELECT … FOR UPDATE` la blocca fino al commit. Le emissioni sulla
  stessa cassa si mettono in fila; casse diverse procedono in parallelo.
- **Contatore, documento ed evento sono nella stessa transazione:** se qualcosa fallisce dopo l'incremento, il
  rollback annulla anche quello, quindi nessun numero va perso (niente buchi). Per questo non si usa
  `AUTO_INCREMENT`, che non torna indietro con il rollback e non è per cassa.
- Il lock è preso il più tardi possibile (dopo controllo dell'abilitazione e calcolo degli importi), così dura
  solo il tempo di numero, INSERT e commit.
- Rete di sicurezza: vincolo `UNIQUE (organization_id, register_id, number)`.
- Il `FOR UPDATE` serve anche a **leggere l'ultimo valore salvato**. Senza, MySQL fa leggere a ogni transazione i
  dati com'erano al suo inizio, e due emissioni concorrenti potrebbero leggere lo stesso contatore. Verificato
  togliendo il lock: il test di concorrenza fallisce.

Verifica: `ReceiptConcurrencyIT`, 20 emissioni concorrenti → numeri esattamente 1..20.

### Idempotenza

**Emissione e annullamento.** Il client manda un header `Idempotency-Key`. Nella stessa transazione del documento
si salva un record `(organizzazione, chiave) → documento, hash SHA-256 della richiesta`.

- Stessa chiave e stessa richiesta → si restituisce il documento già emesso (**200** invece di 201).
- Stessa chiave, richiesta diversa → **422** `IDEMPOTENCY_KEY_REUSED`.
- Il controllo avviene **prima** di quello dell'abilitazione: una ripetizione di un'emissione riuscita restituisce
  il documento anche se nel frattempo l'organizzazione è stata sospesa.
- **Richieste concorrenti con la stessa chiave:** entrambe superano il controllo iniziale, ma la chiave primaria
  del record ne fa vincere una sola. Le altre falliscono all'inserimento, la transazione va in rollback (numero
  compreso) e il client riceve **409**; ripetendo la richiesta ottiene il documento con 200.
- Annullamento: il vincolo `UNIQUE (voided_receipt_id)` garantisce un solo annullamento per documento anche con
  due richieste concorrenti con chiavi diverse.

Verifica: `ReceiptConcurrencyIT`, 20 richieste concorrenti con la stessa chiave → un solo documento, contatore a 1.

**Provisioning delle abilitazioni (Account).** `PUT /api/organizations/{id}/entitlements/{product}` riceve lo
stato completo che l'abilitazione deve avere (stato, validità), non un comando come "abilita" o "disabilita".
Se la richiesta coincide con lo stato attuale non si modifica nulla e **non si pubblica alcun evento**; se cambia,
si aggiorna e si pubblica. Ripetere la stessa richiesta dà quindi sempre lo stesso risultato, senza duplicati né
errori, come prevede il metodo PUT di HTTP.
Due provisioning concorrenti della stessa abilitazione: vincolo `UNIQUE (organization_id, product)` e lock
ottimistico (`@Version`), con risposta 409.

Verifica: `AccountApplicationIT`, richiesta ripetuta → una sola abilitazione e un solo evento; validità diversa →
aggiornamento e nuovo evento.

**Chiusura giornaliera.** Chiudere due volte la stessa cassa nello stesso giorno restituisce la chiusura
esistente; il vincolo `UNIQUE (organization_id, register_id, business_date)` protegge dalle richieste concorrenti.

### Doppia trasmissione

Requisito: nessuna doppia trasmissione della stessa chiusura, anche se il job parte due volte o su due istanze.

La chiusura (`POST …/closures`) crea solo una riga in stato `TO_TRANSMIT` e risponde **202**. La trasmissione la fa
`TransmissionJob`, ogni 5 secondi, in tre passi separati:

1. **Claim atomico**: un `UPDATE … SET status = IN_PROGRESS, lease_until = ?, attempts = attempts + 1
   WHERE id = ? AND (status = TO_TRANSMIT AND next_attempt_at <= now OR status = IN_PROGRESS AND lease_until < now)`.
   Restituisce 1 se la chiusura è stata presa da questa istanza, 0 se l'ha già presa un'altra: su due istanze in
   parallelo ne vince una sola, perché il database rivaluta la condizione in modo atomico. Il job può quindi
   girare su tutte le istanze contemporaneamente, ognuna su chiusure diverse. Un'alternativa sarebbe una libreria
   come ShedLock, che fa girare il job su una sola istanza alla volta; qui non serve, perché la protezione è sulla
   singola chiusura.
2. **Invio HTTP fuori da qualsiasi transazione**: nessun lock né connessione tenuti durante una chiamata che può
   durare secondi.
3. **Esito** in una nuova transazione breve: `TRANSMITTED`; oppure, per errori transitori (timeout, connessione,
   5xx, 429), di nuovo `TO_TRANSMIT` con **attesa crescente tra un tentativo e l'altro** (*backoff esponenziale*: 2 s, 4 s,
   8 s, … fino a un massimo di 60 s, più una piccola variazione casuale, il *jitter*, perché più chiusure fallite
   insieme non riprovino tutte nello stesso istante);
   `FAILED` dopo 5 tentativi o per errori permanenti (4xx).

Macchina a stati: `TO_TRANSMIT → IN_PROGRESS → TRANSMITTED | TO_TRANSMIT (retry) | FAILED`.

**Il caso difficile è il timeout:** non significa "non ricevuto". L'ente può aver registrato la trasmissione e la
risposta essersi persa. Per questo ogni chiusura ha un **`transmissionId` fisso**, inviato a ogni tentativo, e
l'ente deduplica: riceve più volte ma registra una volta sola. Noi garantiamo che la chiusura arrivi **almeno una
volta** (*at-least-once*: nel dubbio si riprova); l'ente, ignorando i duplicati, la registra **una volta sola**.
L'effetto complessivo è quello di una consegna *exactly-once*.

**Istanza che muore durante l'invio:** la chiusura resta `IN_PROGRESS` fino alla scadenza del **lease** (30 s,
molto più del timeout di 3 s), poi torna prendibile da un'altra istanza. Prima di registrare l'esito, il job
rilegge la chiusura con un lock di riga (`SELECT … FOR UPDATE`) e verifica che il lease sia ancora il suo: se nel
frattempo un'altra istanza ha ripreso la chiusura, l'esito viene ignorato. Il lock rende atomici controllo e
aggiornamento: un claim concorrente aspetta il commit e poi non trova più la chiusura prendibile.

Verifica: `ClosureClaimConcurrencyIT`, 10 claim concorrenti sulla stessa chiusura → uno solo riesce. Verificato
anche con lo stub: una chiusura in timeout e poi riprovata risulta "già presente (duplicato)" lato ente, con lo
stesso riferimento esterno.

### Coerenza tra database ed evento pubblicato

Problema: salvare il dato e pubblicare l'evento sono due operazioni su due sistemi. Se si pubblica dopo il commit
e il processo muore, l'evento si perde; se si pubblica prima, può partire un evento per un dato mai salvato.

Soluzione: **transactional outbox** (`platform-outbox`).

- L'evento si scrive nella tabella `outbox_event` **nella stessa transazione** del dato (`OutboxWriter`, con
  `Propagation.MANDATORY`: fallisce se chiamato fuori da una transazione).
- `OutboxRelay`, ogni 500 ms, legge un blocco di eventi non pubblicati con `SELECT … FOR UPDATE SKIP LOCKED` (più
  istanze non prendono le stesse righe), li invia a Kafka **attendendo la conferma del broker** e solo dopo li marca
  come pubblicati.
- Al primo errore si ferma, per non pubblicare eventi successivi della stessa chiave prima di quello fallito
  (ordinamento per chiave); riprova al giro successivo. Con Kafka spento le API continuano a funzionare e gli
  eventi partono, in ordine, quando torna disponibile.
- Garanzia *at-least-once*: se il processo muore tra la conferma del broker e il commit, l'evento viene ripubblicato.
  Per questo i consumer sono idempotenti (version nella copia locale delle abilitazioni).
- **L'ordine per chiave è garantito con un solo relay attivo.** Con più istanze, `SKIP LOCKED` fa prendere a
  ognuna un blocco diverso e i blocchi vengono pubblicati in parallelo: due eventi della stessa chiave possono
  arrivare su Kafka invertiti. La copia locale delle abilitazioni non ne risente, perché applica un evento solo
  se la sua `version` è più recente; un consumer di `receipts.documents` (il CRM) dovrebbe gestire il fuori
  ordine allo stesso modo, come richiede comunque la prova.

---

## Semplificazioni e parti non implementate

- **CRM non implementato**. Ho dato priorità ai requisiti valutati per primi: concorrenza,
  idempotenza, coerenza tra database ed eventi. Il CRM l'avrei realizzato come `crm-service` su **MongoDB**: lo
  storico acquisti è un riepilogo per cliente costruito dagli eventi di Receipts, letto sempre per cliente, senza
  relazioni con altri dati né transazioni su più tabelle. Un documento per cliente rappresenta questo dato in
  modo naturale.
- **MongoDB** di conseguenza è avviato da docker compose ma non usato.
- **La chiusura trasmette un riepilogo, non i singoli documenti**: numero di documenti, primo e ultimo numero,
  totale della giornata. Con un ente reale si invierebbe il dettaglio dei documenti; il meccanismo di
  trasmissione (stati, retry, claim, `transmissionId`) resterebbe lo stesso.
- Un'unica istanza MySQL con due database e due utenti separati (in produzione: istanze distinte).
- Nessuna autenticazione/autorizzazione: l'organizzazione è nel path.
- Un documento emesso dopo la chiusura della sua giornata non viene incluso in quella chiusura: non è gestito.

## Limiti noti

- **`FAILED` non significa "non ricevuto"**: significa che non si è ottenuta conferma entro i tentativi. L'ente può
  aver registrato la chiusura (caso osservato con lo stub sempre lento). Serve una riconciliazione o una
  ritrasmissione manuale, sicura grazie al `transmissionId`.
- **Eventi scartati dal consumer**: se Receipts non riesce a elaborare un evento di Account (ad esempio per un
  bug o un messaggio malformato), dopo alcuni tentativi lo salta per non bloccare gli eventi successivi. La copia
  locale resta allora indietro su quell'organizzazione finché non arriva un suo evento più recente (ogni evento
  porta lo stato completo). Rimedio: un Dead Letter Topic, vedi sotto.
- **Scala dei decimali nelle risposte**: alla prima emissione quantità e prezzi tornano come inviati (`1.2`), in una
  ripetizione idempotente come salvati (`1.20`). I valori sono identici, la rappresentazione no. Per lo stesso
  motivo l'hash di idempotenza distingue `1.0` da `1.00`. Vale anche per `issuedAt`: nanosecondi alla prima
  emissione, microsecondi (la precisione della colonna) in una ripetizione.

## Cosa farei con più tempo

- Il servizio CRM su MongoDB.
- **Dead Letter Topic** per gli eventi che il consumer non riesce a elaborare, con possibilità di riprocessarli.
- Riconciliazione delle chiusure `FAILED` interrogando l'ente per `transmissionId`, e un endpoint per ritrasmetterle.
- Altri test di integrazione: 409 su provisioning concorrenti in Account, 403 per organizzazione sospesa o non
  abilitata, trasmissione completa verso uno stub simulato (retry, `FAILED` dopo i tentativi).
- Normalizzazione della scala dei `BigDecimal` all'ingresso, così risposta e hash non dipendono dalla forma inviata.
- Nella lettura di un documento, un campo calcolato `voidedByReceiptId` con il documento che lo annulla. Oggi
  l'originale è immutabile (per scelta: un documento fiscale emesso non si modifica) e il collegamento esiste solo
  nel documento di annullamento (`voidedReceiptId`); lo stato "annullato" andrebbe derivato in lettura, senza
  toccare l'originale.
- Pulizia periodica degli eventi già pubblicati in `outbox_event` e dei record di idempotenza scaduti.

---

## Uso dell'intelligenza artificiale

Strumento usato: **Claude, tramite Claude Code**, come assistente durante tutto lo sviluppo.

**Con l'AI** (scritto da Claude, poi rivisto, provato e verificato da me):
- lo scheletro del progetto: struttura multi-modulo, configurazione, docker compose, migration, entità, DTO,
  controller, gestione degli errori, propagazione del correlation id, `OutboxWriter`, lo stub dell'ente esterno,
  `TransmissionClient`, configurazione di Testcontainers;
- il calcolo IVA (`VatCalculator`) e il suo test;
- i test di integrazione `ReceiptConcurrencyIT`, `ClosureClaimConcurrencyIT` e i test di provisioning in
  `AccountApplicationIT`, completati a partire da mie bozze;
- `ClosureService.close`, completato a partire da una mia bozza, e `TransmissionJob`;
- singoli pezzi dentro parti scritte da me: il calcolo dell'hash della richiesta in `ReceiptService` e il
  controllo delle date di validità in `EntitlementService`;
- il README, redatto a partire dalle mie note e dalle decisioni prese durante lo sviluppo, e rivisto da me.

**Scritto da me**, con Claude come revisore (spiegazioni dei concetti e review del codice):
- provisioning idempotente delle abilitazioni e cambio di stato delle organizzazioni (Account), mappatura dei
  conflitti di concorrenza in 409;
- l'outbox relay (`OutboxRepository`, `OutboxRelay`);
- la copia locale delle abilitazioni in Receipts (`AccessProjection`, `AccessChecker`);
- numerazione e idempotenza dell'emissione, annullamento (`RegisterCounterRepository`, `ReceiptService`).

**Scritto da me su indicazioni dettagliate di Claude:**
- transizioni di stato della chiusura e claim atomico (`DailyClosure`, `DailyClosureRepository`).

Ho usato Claude anche per discutere le scelte progettuali (documentate sopra) e per verificare i comportamenti
in caso di concorrenza e di guasto.

**Revisione finale prima della consegna**, fatta con Claude e con **ChatGPT**. Ne sono uscite tre correzioni,
scritte da Claude e verificate da me: il lock di riga nella registrazione dell'esito della trasmissione
(`TransmissionJob`, `DailyClosureRepository`), il formato degli errori di validazione sull'emissione e i limiti di
lunghezza sui campi in ingresso (`ReceiptController`, `OrganizationController`).
