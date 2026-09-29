package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ShopProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Figure;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftCash;
import org.junit.jupiter.api.Test;

class ReportPartsTest {

  private static final List<Column> COLUMNS =
      List.of(
          Column.text("product", "Product"),
          Column.count("qty", "Qty"),
          Column.money("sales", "Sales"));

  private static Map<String, Object> row(String product, long qty, String sales) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("product", product);
    r.put("qty", qty);
    r.put("sales", new BigDecimal(sales));
    return r;
  }

  private static ReportTable table(List<Map<String, Object>> rows) {
    return new ReportTable(
        "test",
        "Test report",
        "15 Mar 2027",
        "A note",
        List.of(new Figure("Sales", new BigDecimal("1250.50"), Kind.MONEY)),
        COLUMNS,
        rows,
        ReportTable.totalsOf(COLUMNS, rows));
  }

  @Test
  void rangeIsNairobiDaysAndLimitedToAYear() {
    // 22:00 UTC on 14 March is already 15 March in Nairobi.
    Clock clock = Clock.fixed(Instant.parse("2027-03-14T22:00:00Z"), ZoneOffset.UTC);
    ReportRange today = ReportRange.of(null, null, clock);
    assertThat(today.from()).isEqualTo(LocalDate.of(2027, 3, 15));
    assertThat(today.start()).isEqualTo(Instant.parse("2027-03-14T21:00:00Z"));
    assertThat(today.end()).isEqualTo(Instant.parse("2027-03-15T21:00:00Z"));
    assertThatThrownBy(
            () -> ReportRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), clock))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void totalsAddUpTheSumColumns() {
    Map<String, Object> t = table(List.of(row("A", 2, "100.50"), row("B", 3, "20"))).totals();
    assertThat(t.get("product")).isEqualTo("Total");
    assertThat(t.get("qty")).isEqualTo(5L);
    assertThat((BigDecimal) t.get("sales")).isEqualByComparingTo("120.50");
  }

  @Test
  void valuesAreFormattedForPeopleAndForSpreadsheets() {
    assertThat(ReportFormat.forPeople(new BigDecimal("1250.5"), Kind.MONEY)).isEqualTo("1,250.50");
    assertThat(ReportFormat.forPeople(new BigDecimal("40.0"), Kind.PERCENT)).isEqualTo("40.0%");
    assertThat(ReportFormat.forPeople(Instant.parse("2027-03-15T07:00:00Z"), Kind.DATETIME))
        .isEqualTo("15 Mar 2027 10:00");
    assertThat(ReportFormat.forCsv(new BigDecimal("1250.50"), Kind.MONEY)).isEqualTo("1250.50");
    assertThat(ReportFormat.forPeople(null, Kind.MONEY)).isEmpty();
  }

  @Test
  void csvQuotesCommasAndDefusesFormulas() throws Exception {
    StringWriter out = new StringWriter();
    CsvReport.write(table(List.of(row("Cable, 1m", 1, "300"), row("=HYPERLINK(1)", 1, "5"))), out);
    assertThat(out.toString())
        .isEqualTo(
            "Product,Qty,Sales\r\n"
                + "\"Cable, 1m\",1,300\r\n"
                + "'=HYPERLINK(1),1,5\r\n"
                + "Total,2,305.00\r\n");
  }

  @Test
  void pdfRendersWithAndWithoutRows() {
    ShopProperties shop =
        new ShopProperties("Tavuzi Gadgets", "Nairobi", null, null, null, null, null, null, null);
    byte[] full = PdfReport.render(table(List.of(row("Phone", 1, "15000"))), shop, Instant.EPOCH);
    byte[] empty = PdfReport.render(table(List.of()), shop, Instant.EPOCH);
    assertThat(new String(full, 0, 5)).isEqualTo("%PDF-");
    assertThat(empty.length).isGreaterThan(500);
  }

  @Test
  void dashboardShowsEveryDayEvenWithoutSales() {
    ReportRange range = new ReportRange(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 3));
    Map<String, Object> day2 = new LinkedHashMap<>();
    day2.put("day", LocalDate.of(2027, 3, 2));
    day2.put("net_sales", new BigDecimal("500.00"));
    day2.put("profit", new BigDecimal("100.00"));
    day2.put("sales_count", 2L);
    var points = ReportService.fillDays(range, List.of(day2));
    assertThat(points).hasSize(3);
    assertThat(points.get(0).sales()).isEqualByComparingTo("0");
    assertThat(points.get(1).salesCount()).isEqualTo(2);
  }

  @Test
  void expectedCashIsFloatPlusCashSalesLessRefunds() {
    ShiftCash cash =
        new ShiftCash(
            2,
            new BigDecimal("2500"),
            new BigDecimal("180"),
            new BigDecimal("1160"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    assertThat(cash.cashSales()).isEqualByComparingTo("2320");
    assertThat(cash.expected(new BigDecimal("1000"))).isEqualByComparingTo("2160");
  }
}
