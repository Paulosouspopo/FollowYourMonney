package com.portfolio.tracker.imports.csv;

import java.util.List;

/**
 * Fichier CSV lu : en-têtes + lignes de données.
 *
 * @param encoding  encodage détecté (UTF-8 ou ISO-8859-1)
 * @param delimiter séparateur détecté
 */
public record CsvTable(List<String> headers, List<Line> lines, String encoding, char delimiter) {

    /**
     * Ligne de données.
     *
     * @param number numéro de ligne dans le fichier (1 = en-têtes), affiché à l'utilisateur
     * @param raw    contenu brut (sert à calculer une empreinte anti-doublon)
     */
    public record Line(int number, List<String> cells, String raw) {

        public String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
        }
    }

    /**
     * Index d'une colonne par son nom, -1 si absente. Insensible à la casse,
     * aux espaces et aux accents (« Qté » = « qte », « Prix d'éxé »).
     */
    public int column(String name) {
        String wanted = normalize(name);
        for (int i = 0; i < headers.size(); i++) {
            if (normalize(headers.get(i)).equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    public static String normalize(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    public boolean hasColumns(String... names) {
        for (String name : names) {
            if (column(name) < 0) {
                return false;
            }
        }
        return true;
    }
}
