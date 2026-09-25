package com.portfolio.tracker.imports;

import lombok.Getter;

import java.util.List;

/**
 * Une ligne n'a pas pu être importée : tout l'import est annulé et le front
 * désigne la ligne fautive (400, champ {@code row:<id>}).
 */
@Getter
public class ImportRowException extends RuntimeException {

    private final int rowId;

    public ImportRowException(int rowId, List<Integer> lines, String reason) {
        super("Ligne " + (lines == null || lines.isEmpty() ? "?" : lines.get(0)) + " du fichier : " + reason);
        this.rowId = rowId;
    }
}
