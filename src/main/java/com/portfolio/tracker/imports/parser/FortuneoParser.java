package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.imports.AssetRef;
import com.portfolio.tracker.imports.ImportFormat;
import com.portfolio.tracker.imports.ImportKind;
import com.portfolio.tracker.imports.ImportedOperation;
import com.portfolio.tracker.imports.csv.CsvTable;
import com.portfolio.tracker.imports.csv.Numbers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fortuneo — « Historique des opérations de bourse » (PEA / compte-titres).
 *
 * <pre>libellé;Opération;Place;Date;Qté;Prix d'éxé;Montant brut;Courtage/Prélèvement;Montant net;Devise;</pre>
 *
 * Particularités :
 * <ul>
 * <li>ni symbole ni ISIN : l'actif est retrouvé par son libellé (à valider) ;</li>
 * <li>plus récent en premier : remis dans l'ordre chronologique ;</li>
 * <li>les OST de détachement de coupon (et leurs annulations) n'ont pas
 * d'effet sur le portefeuille : seul l'« Encaissement coupons » est un
 * dividende, la colonne Courtage/Prélèvement porte alors la retenue à la source.</li>
 * </ul>
 */
@Component
public class FortuneoParser implements StatementParser {

    @Override
    public ImportFormat format() {
        return ImportFormat.FORTUNEO;
    }

    @Override
    public boolean supports(CsvTable table) {
        return table.hasColumns("libellé", "Opération", "Place", "Date", "Qté", "Montant net");
    }

    @Override
    public List<ImportedOperation> parse(CsvTable t) {
        int label = t.column("libellé");
        int operation = t.column("Opération");
        int place = t.column("Place");
        int date = t.column("Date");
        int qty = t.column("Qté");
        int price = t.column("Prix d'éxé");
        int gross = t.column("Montant brut");
        int fees = t.column("Courtage/Prélèvement");
        int currency = t.column("Devise");

        ExternalRefs refs = new ExternalRefs("FORTUNEO");
        List<ImportedOperation> result = new ArrayList<>();
        for (CsvTable.Line line : t.lines()) {
            List<Integer> lines = List.of(line.number());
            String op = CsvTable.normalize(line.cell(operation));
            Optional<LocalDateTime> when = DateParsing.parse(line.cell(date));
            if (when.isEmpty()) {
                result.add(ImportedOperation.ignored(lines, null, "Date illisible : « " + line.cell(date) + " »"));
                continue;
            }

            if (op.contains("ost") || op.startsWith("annul")) {
                result.add(ImportedOperation.ignored(lines, when.get(),
                        "Opération sur titre (" + line.cell(operation) + ") : sans effet sur le portefeuille"));
                continue;
            }
            ImportKind kind = op.startsWith("achat") ? ImportKind.BUY
                    : op.startsWith("vente") ? ImportKind.SELL
                    : op.contains("coupon") || op.contains("dividende") ? ImportKind.DIVIDEND
                    : null;
            if (kind == null) {
                result.add(ImportedOperation.ignored(lines, when.get(), "Type d'opération non pris en charge : « "
                        + line.cell(operation) + " »"));
                continue;
            }

            String placeName = line.cell(place);
            AssetRef asset = AssetRef.name(line.cell(label),
                    placeName.isEmpty() || placeName.equalsIgnoreCase("null") ? null : placeName.replace("Euronext ", ""));
            ImportedOperation.ImportedOperationBuilder b = ImportedOperation.builder()
                    .lines(lines)
                    .dateTime(when.get())
                    .kind(kind)
                    .asset(asset)
                    .fees(Numbers.abs(line.cell(fees)))
                    .currency(orEur(line.cell(currency)))
                    .externalRef(refs.of(line.raw()));

            if (kind == ImportKind.DIVIDEND) {
                // Montant brut = dividende avant retenue ; la retenue est comptée en frais
                b.quantity(BigDecimal.ONE).unitPrice(Numbers.abs(line.cell(gross)));
            } else {
                b.quantity(Numbers.abs(line.cell(qty))).unitPrice(Numbers.abs(line.cell(price)));
            }
            result.add(b.build());
        }
        // Export du plus récent au plus ancien
        Collections.reverse(result);
        return result;
    }

    private static String orEur(String currency) {
        return currency == null || currency.isBlank() ? "EUR" : currency.trim().toUpperCase();
    }
}
