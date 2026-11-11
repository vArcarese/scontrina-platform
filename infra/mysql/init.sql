-- Un'istanza MySQL, due database separati con utenti separati:
-- ogni servizio vede SOLO il proprio database (nessun accesso diretto al DB altrui).
-- In produzione sarebbero istanze distinte; qui è una semplificazione dichiarata.

CREATE DATABASE IF NOT EXISTS account_db;
CREATE USER IF NOT EXISTS 'account'@'%' IDENTIFIED BY 'account';
GRANT ALL PRIVILEGES ON account_db.* TO 'account'@'%';

CREATE DATABASE IF NOT EXISTS receipts_db;
CREATE USER IF NOT EXISTS 'receipts'@'%' IDENTIFIED BY 'receipts';
GRANT ALL PRIVILEGES ON receipts_db.* TO 'receipts'@'%';

FLUSH PRIVILEGES;
