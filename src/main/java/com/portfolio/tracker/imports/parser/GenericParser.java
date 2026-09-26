package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.imports.AssetRef;
import com.portfolio.tracker.imports.ImportKind;
import com.portfolio.tracker.imports.ImportedOperation;
import com.portfolio.tracker.imports.csv.CsvTable;
import com.portfolio.tracker.imports.csv.Numbers;
import com.portfolio.tracker.imports.dto.GenericMapping;
import com.portfolio.tracker.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Relevé d'un courtier non reconnu : colonnes associées par l'utilisateur
 * ({@link GenericMapping}). Nombres et dates dans les formats courants.
 */
@Component
public class GenericParser {

    public List<ImportedOperation> parse(CsvTable t, GenericMapping m) {
        int date = required(t, m.dateColumn(), "date");
        int type = required(t, m.typeColumn(), "type d'opération");
        int asset = optional(t, m.assetColumn());
        int quantity = optional(t, m.quantityColumn());
        int price = optional(t, m.priceColumn());
        int amount = optional(t, m.amountColumn());
        int fees = optional(t, m.feesColumn());
        int currency = optional(t, m.currencyColumn());
        Map<String, ImportKind> kinds = m.typeValues() == null ? Map.of() : m.typeValues();
        String defaultCurrency = m.defaultCurrency() == null || m.defaultCurrency().isBlank()
                ? "EUR" : m.defaultCurrency().trim().toUpperCase();

        ExternalRefs refs = new ExternalRefs("IMPORT");
        List<ImportedOperation> result = new ArrayList<>();
        for (CsvTable.Line line : t.lines()) {
            List<Integer> lines = List.of(line.number());
            Optional<LocalDateTime> when = DateParsing.parse(line.cell(date));
            if (when.isEmpty()) {
                result.add(ImportedOperation.error(lines, null, "Date illisible : « " + line.cell(date) + " »"));
                continue;
            }
            String rawType = line.cell(type);
            ImportKind kind = kinds.get(rawType);
            if (kind == null) {
                result.add(ImportedOperation.ignored(lines, when.get(), "Type « " + rawType + " » non associé"));
                continue;
            }
            BigDecimal total = Numbers.abs(line.cell(amount));
            BigDecimal fee = Numbers.abs(line.cell(fees));
            String ccy = currency >= 0 && !line.cell(currency).isEmpty() ? line.cell(currency).toUpperCase() : defaultCurrency;
            ImportedOperation.ImportedOperationBuilder b = ImportedOperation.builder()
                    .lines(lines).dateTime(when.get()).kind(kind).fees(fee).currency(ccy)
                    .externalRef(refs.of(line.raw()));

            if (!kind.isTrade()) {
                result.add(b.amount(total).currency("EUR").build());
                continue;
            }
            String assetValue = line.cell(asset);
            if (assetValue.isEmpty()) {
                result.add(ImportedOperation.error(lines, when.get(), "Actif manquant"));
                continue;
            }
            b.asset(AssetRef.guess(assetValue));
            if (kind == ImportKind.DIVIDEND) {
                result.add(b.quantity(BigDecimal.ONE).unitPrice(total).build());
                continue;
            }
            BigDecimal qty = Numbers.abs(line.cell(quantity));
            BigDecimal unit = Numbers.abs(line.cell(price));
            if (unit.signum() == 0 && qty.signum() > 0 && total.signum() > 0) {
                unit = total.divide(qty, 8, RoundingMode.HALF_UP);
            }
            result.add(b.quantity(qty).unitPrice(unit).build());
        }
        result.sort(Comparator.comparing(ImportedOperation::getDateTime, Comparator.nullsFirst(Comparator.naturalOrder())));
        return result;
    }

    private static int required(CsvTable t, String column, String what) {
        int index = optional(t, column);
        if (index < 0) {
            throw new BadRequestException("Associe une colonne pour : " + what);
        }
        return index;
    }

    private static int optional(CsvTable t, String column) {
        return column == null || column.isBlank() ? -1 : t.column(column);
    }
}
