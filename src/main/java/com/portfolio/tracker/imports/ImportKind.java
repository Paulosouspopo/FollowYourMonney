package com.portfolio.tracker.imports;

import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.transaction.TransactionType;

/** Nature d'une opération importée : opération sur actif ou mouvement d'argent. */
public enum ImportKind {
    BUY, SELL, DIVIDEND,
    DEPOSIT, WITHDRAWAL, INTEREST, FEE;

    public boolean isTrade() {
        return this == BUY || this == SELL || this == DIVIDEND;
    }

    public TransactionType toTransactionType() {
        return TransactionType.valueOf(name());
    }

    public CashMovementType toCashMovementType() {
        return CashMovementType.valueOf(name());
    }
}
