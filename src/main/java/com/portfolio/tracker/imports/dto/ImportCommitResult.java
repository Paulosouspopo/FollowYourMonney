package com.portfolio.tracker.imports.dto;

/**
 * @param skipped lignes déjà importées (même référence dans le relevé) : ignorées
 */
public record ImportCommitResult(int transactions, int cashMovements, int skipped) {}
