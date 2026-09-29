package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shifts and reports end to end. Everything happens on its own day (15 March 2027) so the numbers
 * are exact: two sales (cash with change, M-Pesa), one cash return, and a shift closed KES 20
 * short.
 */
class ReportsFlowTest extends ApiTest {

  private static final String DAY = "?from=2027-03-15&to=2027-03-15";

  @Autowired private MutableClock clock;
  private Instant before;

  // The clock is shared by all test classes: put it back afterwards.
  @BeforeEach
  void onReportDay() {
    before = clock.instant();
    clock.set(Instant.parse("2027-03-15T07:00:00Z")); // 10:00 in Nairobi
  }

  @AfterEach
  void restoreClock() {
    clock.set(before);
  }

  private JsonNode product(String owner, String name, String vat, int price, int cost, int stock)
      throws Exception {
    Map<String, Object> p = new HashMap<>();
    p.put("name", name + " R" + SEQ.incrementAndGet());
    p.put("category", "Reports");
    p.put("vatCategory", vat);
    p.put("serialType", "NONE");
    p.put("warrantyMonths", 0);
    p.put("costPrice", cost);
    p.put("active", true);
    p.put("units", List.of(Map.of("name", "Piece", "factor", 1, "retailPrice", price)));
    JsonNode created = body(post(owner, "/api/products", p).andExpect(status().isCreated()));
    post(
            owner,
            "/api/stock/adjustments",
            Map.of(
                "productId",
                created.path("id").asLong(),
                "quantity",
                stock,
                "reason",
                "OPENING_STOCK"))
        .andExpect(status().isOk());
    return created;
  }

  private static Map<String, Object> sale(JsonNode product, int qty, Map<String, Object> payment) {
    Map<String, Object> s =
        cashSale(
            product.path("id").asLong(),
            product.path("units").get(0).path("id").asLong(),
            qty,
            0,
            null);
    s.put("payments", List.of(payment));
    return s;
  }

  /** The row whose column "key" contains the text. */
  private static JsonNode row(JsonNode report, String key, String text) {
    for (JsonNode r : report.path("rows")) {
      if (r.path(key).asText().contains(text)) {
        return r;
      }
    }
    throw new AssertionError("No row with " + text + " in " + report.path("rows"));
  }

  @Test
  void shiftsAndReportsAddUp() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    String manager = newUser("MANAGER");
    JsonNode earbuds = product(owner, "Earbuds", "STANDARD", 1160, 600, 10);
    JsonNode cover = product(owner, "Cover", "EXEMPT", 500, 200, 5);
    JsonNode tv = product(owner, "Slow TV", "STANDARD", 30000, 20000, 2);
    String code = "RPT" + String.format("%07d", SEQ.incrementAndGet());

    // Shift: open with KES 1,000 float; only one open shift at a time.
    get(cashier, "/api/shifts/current").andExpect(status().isNoContent());
    long shiftId =
        body(post(cashier, "/api/shifts/open", Map.of("openingFloat", 1000))
                .andExpect(status().isCreated()))
            .path("id")
            .asLong();
    post(cashier, "/api/shifts/open", Map.of("openingFloat", 0)).andExpect(status().isConflict());

    // Sale 1: 2 earbuds (2,320) paid with 2,500 cash; sale 2: a cover by M-Pesa.
    JsonNode sale1 =
        body(
            post(cashier, "/api/sales", sale(earbuds, 2, Map.of("method", "CASH", "amount", 2500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeDue").value(180.00)));
    post(
            cashier,
            "/api/sales",
            sale(cover, 1, Map.of("method", "MPESA_TILL", "amount", 500, "reference", code)))
        .andExpect(status().isCreated());
    // One earbud comes back, refunded in cash (approved by the owner).
    Map<String, Object> ret = new HashMap<>();
    ret.put(
        "lines",
        List.of(
            Map.of("saleLineId", sale1.path("lines").get(0).path("id").asLong(), "quantity", 1)));
    ret.put("reason", "Left bud silent");
    ret.put("refundMethod", "CASH");
    ret.put("approval", Map.of("username", "owner", "password", "owner-pass-123"));
    post(cashier, "/api/sales/" + sale1.path("id").asLong() + "/returns", ret)
        .andExpect(status().isCreated());

    // The till sees its open shift, but not the expected cash (the count is blind).
    JsonNode current = body(get(cashier, "/api/shifts/current").andExpect(status().isOk()));
    assertThat(current.path("salesCount").asLong()).isEqualTo(2);
    assertThat(current.has("expectedCash")).isFalse();

    // Expected: 1,000 float + 2,320 cash sales - 1,160 refund = 2,160; counted 2,140.
    String otherCashier = newUser("CASHIER");
    post(otherCashier, "/api/shifts/" + shiftId + "/close", Map.of("countedCash", 0))
        .andExpect(status().isNotFound());
    post(cashier, "/api/shifts/" + shiftId + "/close", Map.of("countedCash", 2140, "note", "Short"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shift.expectedCash").value(2160.00))
        .andExpect(jsonPath("$.shift.difference").value(-20.00))
        .andExpect(jsonPath("$.mpesa").value(500.00));
    post(cashier, "/api/shifts/" + shiftId + "/close", Map.of("countedCash", 2140))
        .andExpect(status().isConflict());

    // A manager can close a shift a cashier left open.
    long left =
        body(post(otherCashier, "/api/shifts/open", Map.of("openingFloat", 500)))
            .path("id")
            .asLong();
    post(manager, "/api/shifts/" + left + "/close", Map.of("countedCash", 500))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shift.difference").value(0.00));

    // Daily summary: sold 2,820, returned 1,160; VAT 320 - 160; profit (1,100) - (400) = 700.
    JsonNode daily = body(get(owner, "/api/reports/daily-sales" + DAY).andExpect(status().isOk()));
    JsonNode day = daily.path("rows").get(0);
    assertThat(daily.path("rows")).hasSize(1);
    assertThat(day.path("day").asText()).isEqualTo("2027-03-15");
    assertThat(day.path("sales_count").asLong()).isEqualTo(2);
    assertThat(day.path("sales").decimalValue()).isEqualByComparingTo("2820");
    assertThat(day.path("refunds").decimalValue()).isEqualByComparingTo("1160");
    assertThat(day.path("net_sales").decimalValue()).isEqualByComparingTo("1660");
    assertThat(day.path("vat").decimalValue()).isEqualByComparingTo("160");
    assertThat(day.path("profit").decimalValue()).isEqualByComparingTo("700");
    assertThat(day.path("cash").decimalValue()).isEqualByComparingTo("2320");
    assertThat(day.path("mpesa").decimalValue()).isEqualByComparingTo("500");
    assertThat(daily.path("totals").path("profit").decimalValue()).isEqualByComparingTo("700");

    // Profit per product (net of the return).
    JsonNode profit = body(get(owner, "/api/reports/product-profit" + DAY));
    JsonNode buds = row(profit, "product", earbuds.path("name").asText());
    assertThat(buds.path("quantity").asLong()).isEqualTo(1);
    assertThat(buds.path("profit").decimalValue()).isEqualByComparingTo("400");
    assertThat(buds.path("margin").decimalValue()).isEqualByComparingTo("40.0");
    assertThat(row(profit, "product", cover.path("name").asText()).path("profit").decimalValue())
        .isEqualByComparingTo("300");

    // VAT: standard-rated 320 on sales less 160 on the return; the exempt cover has none.
    JsonNode vat = body(get(owner, "/api/reports/vat" + DAY));
    JsonNode standard = row(vat, "vat_class", "Standard rated 16%");
    assertThat(standard.path("sales_net").decimalValue()).isEqualByComparingTo("2000");
    assertThat(standard.path("returns_vat").decimalValue()).isEqualByComparingTo("160");
    assertThat(row(vat, "vat_class", "Exempt").path("vat_payable").decimalValue())
        .isEqualByComparingTo("0");
    assertThat(vat.path("totals").path("vat_payable").decimalValue()).isEqualByComparingTo("160");

    // Sales per cashier, with the shift's shortage.
    JsonNode perCashier = body(get(owner, "/api/reports/cashiers" + DAY));
    JsonNode mine = perCashier.path("rows").get(0);
    assertThat(mine.path("sales").decimalValue()).isEqualByComparingTo("2820");
    assertThat(mine.path("average").decimalValue()).isEqualByComparingTo("1410");
    assertThat(mine.path("refunds").decimalValue()).isEqualByComparingTo("1160");
    assertThat(mine.path("cash_difference").decimalValue()).isEqualByComparingTo("-20");

    // Shift cash-ups.
    JsonNode shifts = body(get(manager, "/api/reports/shifts" + DAY));
    assertThat(shifts.path("rows")).hasSize(2);
    assertThat(shifts.path("totals").path("difference").decimalValue()).isEqualByComparingTo("-20");

    // Stock value, best and slow sellers.
    JsonNode stock = body(get(owner, "/api/reports/stock-value" + DAY));
    JsonNode tvRow = row(stock, "product", tv.path("name").asText());
    assertThat(tvRow.path("cost_value").decimalValue()).isEqualByComparingTo("40000");
    assertThat(tvRow.path("retail_value").decimalValue()).isEqualByComparingTo("60000");
    JsonNode best = body(get(owner, "/api/reports/best-sellers" + DAY));
    row(best, "product", earbuds.path("name").asText());
    JsonNode slow = body(get(owner, "/api/reports/slow-sellers" + DAY));
    assertThat(row(slow, "product", tv.path("name").asText()).path("quantity").asLong()).isZero();

    // M-Pesa: no statement imported for this day, so the code is still to be checked.
    JsonNode mpesa = body(get(owner, "/api/reports/mpesa" + DAY));
    assertThat(row(mpesa, "code", code).path("result").asText()).isEqualTo("Not in statement");

    // Dashboard numbers match the reports.
    get(owner, "/api/reports/dashboard" + DAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kpis.netSales").value(1660.00))
        .andExpect(jsonPath("$.kpis.salesCount").value(2))
        .andExpect(jsonPath("$.kpis.profit").value(700.00))
        .andExpect(jsonPath("$.kpis.cashDifference").value(-20.00))
        .andExpect(jsonPath("$.days.length()").value(1))
        .andExpect(jsonPath("$.payments[0].amount").value(2320.00))
        .andExpect(jsonPath("$.topProducts[0].label").value(earbuds.path("name").asText()));
  }

  @Test
  void reportsExportAsPdfAndCsv() throws Exception {
    String owner = owner();
    MvcResult pdf =
        get(owner, "/api/reports/daily-sales/export" + DAY + "&format=pdf")
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString(
                            "tavuzi-daily-sales-2027-03-15-to-2027-03-15.pdf")))
            .andReturn();
    assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

    // Every report renders to PDF, including the wide landscape ones.
    for (ReportName name : ReportName.values()) {
      get(owner, "/api/reports/" + name.slug() + "/export" + DAY).andExpect(status().isOk());
    }

    MvcResult csv =
        get(owner, "/api/reports/vat/export" + DAY + "&format=csv")
            .andExpect(status().isOk())
            .andReturn();
    csv.getAsyncResult();
    String text =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(
                    csv))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(text).startsWith("VAT class,Sales excl. VAT,VAT on sales");
  }

  @Test
  void onlyOwnersAndManagersSeeReports() throws Exception {
    String cashier = newUser("CASHIER");
    get(cashier, "/api/reports/dashboard").andExpect(status().isForbidden());
    get(cashier, "/api/reports/daily-sales/export").andExpect(status().isForbidden());
    String owner = owner();
    get(owner, "/api/reports/no-such-report").andExpect(status().isNotFound());
    get(owner, "/api/reports/daily-sales?from=2027-03-15&to=2027-03-01")
        .andExpect(status().isBadRequest());
    get(owner, "/api/reports/daily-sales/export?format=docx").andExpect(status().isBadRequest());
    post(cashier, "/api/shifts/open", Map.of("openingFloat", -5))
        .andExpect(status().isBadRequest());
  }
}
