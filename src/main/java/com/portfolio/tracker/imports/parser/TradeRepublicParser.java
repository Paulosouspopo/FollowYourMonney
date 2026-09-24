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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Trade Republic — « Exportation de transactions ».
 *
 * Colonnes utiles : datetime (UTC), category (TRADING, CASH, DELIVERY), type,
 * asset_class, name, symbol (ISIN, ou code pour la crypto), shares, price,
 * amount (signé), fee, tax, currency, description, transaction_id.
 *
 * Particularités :
 * <ul>
 * <li>transaction_id unique : anti-doublon exact ;</li>
 * <li>frais et taxe (TTF) séparés : additionnés en frais ;</li>
 * <li>dividende : amount = net en EUR, tax = retenue → brut = net + retenue ;</li>
 * <li>MIGRATION : sortie puis entrée du même titre (changement interne de
 * dépositaire), sans effet : ignoré ;</li>
 * <li>STOCKPERK : bonus en espèces offert → compté en intérêts.</li>
 * </ul>
 */
@Component
public class TradeRepublicParser implements StatementParser {

    @Override
    public ImportFormat format() {
        return ImportFormat.TRADE_REPUBLIC;
    }

    @Override
    public boolean supports(CsvTable table) {
        return table.hasColumns("datetime", "category", "type", "symbol", "shares", "amount", "transaction_id");
    }

    @Override
    public List<ImportedOperation> parse(CsvTable t) {
        Columns c = new Columns(t);
        List<ImportedOperation> result = new ArrayList<>();
        for (CsvTable.Line line : t.lines()) {
            result.add(parseLine(line, c));
        }
        result.sort(Comparator.comparing(ImportedOperation::getDateTime, Comparator.nullsFirst(Comparator.naturalOrder())));
        return result;
    }

    private ImportedOperation parseLine(CsvTable.Line line, Columns c) {
        List<Integer> lines = List.of(line.number());
        Optional<LocalDateTime> when = DateParsing.parse(line.cell(c.datetime));
        if (when.isEmpty()) {
            return ImportedOperation.ignored(lines, null, "Date illisible : « " + line.cell(c.datetime) + " »");
        }
        String category = line.cell(c.category).toUpperCase();
        String type = line.cell(c.type).toUpperCase();
        String ref = "TR:" + line.cell(c.id);

        if (category.equals("DELIVERY") && type.equals("MIGRATION")) {
            return ImportedOperation.ignored(lines, when.get(),
                    "Transfert interne Trade Republic (sortie puis entrée du même titre) : sans effet");
        }

        if (category.equals("TRADING") && (type.equals("BUY") || type.equals("SELL"))) {
            return ImportedOperation.builder()
                    .lines(lines)
                    .dateTime(when.get())
                    .kind(type.equals("BUY") ? ImportKind.BUY : ImportKind.SELL)
                    .asset(asset(line, c))
                    .quantity(Numbers.abs(line.cell(c.shares)))
                    .unitPrice(Numbers.abs(line.cell(c.price)))
                    .fees(Numbers.abs(line.cell(c.fee)).add(Numbers.abs(line.cell(c.tax))))
                    .currency(currency(line, c))
                    .notes(line.cell(c.description).startsWith("Savings plan") ? "Plan d'investissement" : null)
                    .externalRef(ref)
                    .build();
        }

        if (category.equals("CASH") && type.equals("DIVIDEND")) {
            BigDecimal tax = Numbers.abs(line.cell(c.tax));
            BigDecimal gross = Numbers.abs(line.cell(c.amount)).add(tax);
            return ImportedOperation.builder()
                    .lines(lines)
                    .dateTime(when.get())
                    .kind(ImportKind.DIVIDEND)
                    .asset(asset(line, c))
                    .quantity(BigDecimal.ONE)
                    .unitPrice(gross)
                    .fees(tax.add(Numbers.abs(line.cell(c.fee))))
                    .currency(currency(line, c))
                    .externalRef(ref)
                    .build();
        }

        if (category.equals("CASH")) {
            ImportKind kind = cashKind(type);
            if (kind == null) {
                return ImportedOperation.ignored(lines, when.get(), "Type non pris en charge : " + type);
            }
            return ImportedOperation.builder()
                    .lines(lines)
                    .dateTime(when.get())
                    .kind(kind)
                    .amount(Numbers.abs(line.cell(c.amount)))
                    .currency("EUR")
                    .notes(type.equals("STOCKPERK") ? "Bonus Stockperk" : null)
                    .externalRef(ref)
                    .build();
        }

        return ImportedOperation.ignored(lines, when.get(), "Type non pris en charge : " + category + " / " + type);
    }

    private static ImportKind cashKind(String type) {
        if (type.contains("INBOUND") || type.equals("CUSTOMER_INPAYMENT")) {
            return ImportKind.DEPOSIT;
        }
        if (type.contains("OUTBOUND") || type.contains("PAYOUT_TO_CUSTOMER")) {
            return ImportKind.WITHDRAWAL;
        }
        if (type.contains("INTEREST") || type.equals("STOCKPERK")) {
            return ImportKind.INTEREST;
        }
        return null;
    }

    private static AssetRef asset(CsvTable.Line line, Columns c) {
        String symbol = line.cell(c.symbol);
        String name = line.cell(c.name);
        if (line.cell(c.assetClass).equalsIgnoreCase("CRYPTO")) {
            return AssetRef.crypto(symbol, name);
        }
        return AssetRef.looksLikeIsin(symbol) ? AssetRef.isin(symbol, name) : AssetRef.name(name, null);
    }

    private static String currency(CsvTable.Line line, Columns c) {
        String currency = line.cell(c.currency);
        return currency.isEmpty() ? "EUR" : currency.toUpperCase();
    }

    /** Index des colonnes, résolus une fois. */
    private static final class Columns {
        final int datetime, category, type, assetClass, name, symbol, shares, price, amount, fee, tax, currency,
                description, id;

        Columns(CsvTable t) {
            datetime = t.column("datetime");
            category = t.column("category");
            type = t.column("type");
            assetClass = t.column("asset_class");
            name = t.column("name");
            symbol = t.column("symbol");
            shares = t.column("shares");
            price = t.column("price");
            amount = t.column("amount");
            fee = t.column("fee");
            tax = t.column("tax");
            currency = t.column("currency");
            description = t.column("description");
            id = t.column("transaction_id");
        }
    }
}
