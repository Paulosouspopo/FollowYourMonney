package com.portfolio.tracker.imports.csv;

import com.portfolio.tracker.shared.exception.BadRequestException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture tolérante des exports de courtiers :
 * <ul>
 * <li>encodage : UTF-8 (avec ou sans BOM), sinon ISO-8859-1 (Fortuneo) ;</li>
 * <li>séparateur : point-virgule, virgule ou tabulation, déduit des en-têtes ;</li>
 * <li>guillemets RFC 4180 (champ entre guillemets, guillemet doublé) ;</li>
 * <li>lignes vides ignorées, fins de ligne CRLF ou LF.</li>
 * </ul>
 * Écrit à la main plutôt qu'avec une bibliothèque : le besoin est simple et
 * borné (fichiers de quelques milliers de lignes au plus).
 */
public final class CsvReader {

    private static final char QUOTE = '"';

    private CsvReader() {
    }

    public static CsvTable read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Le fichier est vide");
        }
        Decoded decoded = decode(content);
        List<String> physicalLines = splitLines(decoded.text());

        // Première ligne non vide = en-têtes
        int headerIndex = 0;
        while (headerIndex < physicalLines.size() && physicalLines.get(headerIndex).isBlank()) {
            headerIndex++;
        }
        if (headerIndex >= physicalLines.size()) {
            throw new BadRequestException("Le fichier ne contient aucune ligne");
        }
        String headerLine = physicalLines.get(headerIndex);
        char delimiter = detectDelimiter(headerLine);
        List<String> headers = stripTrailingEmpty(parseLine(headerLine, delimiter));

        List<CsvTable.Line> lines = new ArrayList<>();
        for (int i = headerIndex + 1; i < physicalLines.size(); i++) {
            String raw = physicalLines.get(i);
            if (!raw.isBlank()) {
                lines.add(new CsvTable.Line(i + 1, parseLine(raw, delimiter), raw));
            }
        }
        return new CsvTable(headers, lines, decoded.encoding(), delimiter);
    }

    // ------------------------------------------------------------------ interne

    private record Decoded(String text, String encoding) {
    }

    private static Decoded decode(byte[] content) {
        int offset = 0;
        boolean bom = content.length >= 3
                && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF;
        if (bom) {
            offset = 3; // BOM UTF-8 (Binance)
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset))
                    .toString();
            return new Decoded(text, "UTF-8");
        } catch (CharacterCodingException e) {
            // Octets invalides en UTF-8 : export Windows « Latin-1 » (Fortuneo)
            return new Decoded(new String(content, offset, content.length - offset, StandardCharsets.ISO_8859_1),
                    "ISO-8859-1");
        }
    }

    /** Découpe en lignes en respectant les retours à la ligne situés entre guillemets. */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == QUOTE) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lines.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static char detectDelimiter(String headerLine) {
        char best = ',';
        int bestCount = 0;
        for (char candidate : new char[] { ';', ',', '\t' }) {
            int count = parseLine(headerLine, candidate).size();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    static List<String> parseLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == QUOTE && i + 1 < line.length() && line.charAt(i + 1) == QUOTE) {
                    cell.append(QUOTE);
                    i++;
                } else if (c == QUOTE) {
                    inQuotes = false;
                } else {
                    cell.append(c);
                }
            } else if (c == QUOTE) {
                inQuotes = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /** Fortuneo termine chaque ligne par un séparateur : colonne vide finale à ignorer. */
    private static List<String> stripTrailingEmpty(List<String> headers) {
        List<String> result = new ArrayList<>(headers);
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
