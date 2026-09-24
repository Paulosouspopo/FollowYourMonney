package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.imports.ImportKind;
import com.portfolio.tracker.imports.ImportedOperation;
import com.portfolio.tracker.imports.RowStatus;
import com.portfolio.tracker.imports.csv.CsvReader;
import com.portfolio.tracker.imports.csv.CsvTable;
import com.portfolio.tracker.imports.dto.GenericMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Relevés fictifs (src/test/resources/imports), aux formats exacts des courtiers. */
@DisplayName("Parsers de relevés")
class StatementParsersTest {

    private static CsvTable read(String name) throws IOException {
        try (InputStream in = StatementParsersTest.class.getResourceAsStream("/imports/" + name)) {
            return CsvReader.read(in.readAllBytes());
        }
    }

    private static List<ImportedOperation> ready(List<ImportedOperation> ops) {
        return ops.stream().filter(o -> o.getStatus() == RowStatus.READY).toList();
    }

    @Nested
    @DisplayName("Fortuneo")
    class Fortuneo {

        private final FortuneoParser parser = new FortuneoParser();

        @Test
        @DisplayName("Latin-1, en-têtes accentués, ordre chronologique, OST ignorée, dividende brut + retenue")
        void releve() throws IOException {
            CsvTable table = read("fortuneo.csv");
            assertThat(table.encoding()).isEqualTo("ISO-8859-1");
            assertThat(parser.supports(table)).isTrue();

            List<ImportedOperation> ops = parser.parse(table);
            assertThat(ops).extracting(ImportedOperation::getStatus).containsExactly(
                    RowStatus.READY, RowStatus.READY, RowStatus.IGNORED, RowStatus.READY, RowStatus.READY);

            ImportedOperation firstBuy = ops.get(0);
            assertThat(firstBuy.getKind()).isEqualTo(ImportKind.BUY);
            assertThat(firstBuy.getDateTime()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
            assertThat(firstBuy.getQuantity()).isEqualByComparingTo("2");
            assertThat(firstBuy.getUnitPrice()).isEqualByComparingTo("99");
            assertThat(firstBuy.getAsset().label()).isEqualTo("FAKE ETF WORLD UCITS ETF - C EUR ACC");
            assertThat(firstBuy.getAsset().preferredExchange()).isEqualTo("Paris");

            ImportedOperation sell = ops.get(1);
            assertThat(sell.getKind()).isEqualTo(ImportKind.SELL);
            assertThat(sell.getFees()).isEqualByComparingTo("0.5");

            ImportedOperation dividend = ops.get(3);
            assertThat(dividend.getKind()).isEqualTo(ImportKind.DIVIDEND);
            assertThat(dividend.getUnitPrice()).isEqualByComparingTo("20.1");
            assertThat(dividend.getFees()).isEqualByComparingTo("2.91");
            assertThat(dividend.getCurrency()).isEqualTo("USD");
            assertThat(dividend.getAsset().preferredExchange()).isNull();

            assertThat(ops).extracting(ImportedOperation::getExternalRef).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("Trade Republic")
    class TradeRepublic {

        private final TradeRepublicParser parser = new TradeRepublicParser();

        @Test
        @DisplayName("Achats, crypto, dividende net + taxe, versement/retrait, bonus, migration ignorée")
        void releve() throws IOException {
            CsvTable table = read("trade-republic.csv");
            assertThat(parser.supports(table)).isTrue();
            List<ImportedOperation> ops = parser.parse(table);

            assertThat(ops).hasSize(9);
            assertThat(ready(ops)).extracting(ImportedOperation::getKind).containsExactly(
                    ImportKind.DEPOSIT, ImportKind.BUY, ImportKind.BUY, ImportKind.DIVIDEND,
                    ImportKind.SELL, ImportKind.WITHDRAWAL, ImportKind.INTEREST);

            ImportedOperation stock = ready(ops).get(1);
            assertThat(stock.getAsset().reference()).isEqualTo("ISIN:US0000000001");
            assertThat(stock.getFees()).isEqualByComparingTo("1.60"); // frais + taxe
            assertThat(stock.getExternalRef()).isEqualTo("TR:tr-2");

            ImportedOperation crypto = ready(ops).get(2);
            assertThat(crypto.getAsset().reference()).isEqualTo("CRYPTO:BTC");
            // 23:30 UTC = 00:30 le lendemain à Paris
            assertThat(crypto.getDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 11, 0, 30));
            assertThat(crypto.getNotes()).isEqualTo("Plan d'investissement");

            ImportedOperation dividend = ready(ops).get(3);
            assertThat(dividend.getQuantity()).isEqualByComparingTo("1");
            assertThat(dividend.getUnitPrice()).isEqualByComparingTo("1.65");
            assertThat(dividend.getFees()).isEqualByComparingTo("0.22");

            assertThat(ready(ops).get(5).getAmount()).isEqualByComparingTo("50");
            assertThat(ready(ops).get(6).getNotes()).isEqualTo("Bonus Stockperk");

            assertThat(ops).filteredOn(o -> o.getStatus() == RowStatus.IGNORED)
                    .extracting(ImportedOperation::getMessage)
                    .anyMatch(m -> m.contains("Transfert interne"))
                    .anyMatch(m -> m.contains("SOMETHING_NEW"));
        }
    }

    @Nested
    @DisplayName("Binance")
    class Binance {

        private final BinanceParser parser = new BinanceParser();

        @Test
        @DisplayName("Jambes appariées, frais déduits, conversion crypto→crypto estimée, récompenses ignorées")
        void releve() throws IOException {
            CsvTable table = read("binance.csv");
            assertThat(table.headers().get(0)).isEqualTo("User ID"); // BOM retiré
            assertThat(parser.supports(table)).isTrue();
            List<ImportedOperation> ops = parser.parse(table);
            List<ImportedOperation> ready = ready(ops);

            assertThat(ready).extracting(o -> o.getKind() + " " + (o.getAsset() == null ? "EUR" : o.getAsset().cryptoCode()))
                    .containsExactly("DEPOSIT EUR", "BUY BTC", "BUY SOL", "BUY USDT", "SELL USDT", "BUY ETH", "SELL BTC");

            assertThat(ready.get(1).getUnitPrice()).isEqualByComparingTo("50000");
            assertThat(ready.get(2).getUnitPrice()).isEqualByComparingTo("100");

            // 309.81 reçus - 0.30981 de frais = quantité réellement détenue
            ImportedOperation usdt = ready.get(3);
            assertThat(usdt.getQuantity()).isEqualByComparingTo("309.50019");
            assertThat(usdt.getQuantity().multiply(usdt.getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("300.00");

            ImportedOperation swapSell = ready.get(4);
            ImportedOperation swapBuy = ready.get(5);
            assertThat(swapSell.isPriceEstimated()).isTrue();
            assertThat(swapBuy.isPriceEstimated()).isTrue();
            assertThat(swapBuy.getValuationAsset().cryptoCode()).isEqualTo("USDT");
            assertThat(swapBuy.getValuationQuantity()).isEqualByComparingTo("309.50019");
            assertThat(swapSell.getExternalRef()).isNotEqualTo(swapBuy.getExternalRef());

            assertThat(ready.get(6).getUnitPrice()).isEqualByComparingTo("60000");

            assertThat(ops).filteredOn(o -> o.getStatus() == RowStatus.IGNORED)
                    .extracting(ImportedOperation::getMessage)
                    .anyMatch(m -> m.startsWith("Récompense"))
                    .anyMatch(m -> m.startsWith("Conversion d'une récompense"));
        }
    }

    @Nested
    @DisplayName("Générique")
    class Generique {

        @Test
        @DisplayName("Colonnes associées, types traduits, prix déduit du montant, nombres à la française")
        void mapping() {
            String csv = """
                    Date;Sens;Titre;Quantité;Montant;Frais
                    15/01/2026;Achat;FR0000133308;10;"1 234,50";2,5
                    16/01/2026;Versement;;;500;
                    17/01/2026;Inconnu;FR0000133308;1;10;0
                    """;
            CsvTable table = CsvReader.read(csv.getBytes(StandardCharsets.UTF_8));
            List<ImportedOperation> ops = new GenericParser().parse(table, new GenericMapping(
                    "Date", "Sens", Map.of("Achat", ImportKind.BUY, "Versement", ImportKind.DEPOSIT),
                    "Titre", "Quantité", null, "Montant", "Frais", null, null));

            assertThat(ops).hasSize(3);
            ImportedOperation buy = ops.get(0);
            assertThat(buy.getAsset().reference()).isEqualTo("ISIN:FR0000133308");
            assertThat(buy.getUnitPrice()).isEqualByComparingTo("123.45");
            assertThat(buy.getFees()).isEqualByComparingTo("2.5");
            assertThat(ops.get(1).getKind()).isEqualTo(ImportKind.DEPOSIT);
            assertThat(ops.get(1).getAmount()).isEqualByComparingTo("500");
            assertThat(ops.get(2).getStatus()).isEqualTo(RowStatus.IGNORED);
        }
    }
}
