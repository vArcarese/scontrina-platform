package it.scontrina.receipts.receipt;

public enum ReceiptType {
    /** Documento di vendita. */
    SALE,
    /** Documento di annullamento: riferisce l'originale, che resta invariato. */
    VOID
}
