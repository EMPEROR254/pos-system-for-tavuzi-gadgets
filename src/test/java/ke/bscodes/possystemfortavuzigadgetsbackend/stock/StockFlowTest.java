package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.SerialsTest;
import org.junit.jupiter.api.Test;

/**
 * Stock end to end: deliveries from suppliers (stock in, IMEIs on the shelf, average cost, what is
 * owed), sales and returns moving stock, low-stock alerts, adjustments and stock counts.
 */
class StockFlowTest extends ApiTest {

  /** The unique number in a test product's name ("Cable #5007" → "5007"), to search by. */
  private static String number(JsonNode product) {
    return product.path("name").asText().replaceAll(".*#", "");
  }

  private long supplier(String owner) throws Exception {
    return body(post(
                owner,
                "/api/suppliers",
                Map.of(
                    "name",
                    "Supplier " + SEQ.incrementAndGet(),
                    "phone",
                    "0711000000",
                    "active",
                    true))
            .andExpect(status().isCreated()))
        .path("id")
        .asLong();
  }

  private static Map<String, Object> line(
      long product, long unit, int qty, int cost, List<String> serials) {
    Map<String, Object> m = new HashMap<>();
    m.put("productId", product);
    m.put("unitId", unit);
    m.put("quantity", qty);
    m.put("unitCost", cost);
    m.put("serials", serials);
    return m;
  }

  @Test
  void deliveryAddsStockImeisAndWhatIsOwed() throws Exception {
    String owner = owner();
    long supplierId = supplier(owner);
    JsonNode phone = createProduct(owner, "Phone", "IMEI", 15000, null);
    JsonNode cable =
        createProduct(
            owner,
            "Cable",
            "NONE",
            200,
            Map.of("name", "Carton of 20", "factor", 20, "retailPrice", 3600));
    int n = SEQ.incrementAndGet();
    String imei1 = SerialsTest.imei("35111111" + String.format("%06d", n));
    String imei2 = SerialsTest.imei("35222222" + String.format("%06d", n));

    Map<String, Object> delivery = new HashMap<>();
    delivery.put("supplierId", supplierId);
    delivery.put("invoiceNo", "INV-" + n);
    delivery.put(
        "lines",
        List.of(
            line(
                phone.path("id").asLong(),
                phone.path("units").get(0).path("id").asLong(),
                2,
                12000,
                List.of(imei1, imei2)),
            line(
                cable.path("id").asLong(),
                cable.path("units").get(1).path("id").asLong(),
                2,
                2000,
                null)));
    delivery.put("amountPaid", 10000);
    delivery.put("paymentMethod", "MPESA");
    post(owner, "/api/purchases", delivery)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(28000.00));

    // 2 cartons of 20 = 40 cables at 100 each; phones cost 12,000 each.
    get(owner, "/api/stock?q=" + number(cable))
        .andExpect(jsonPath("$.content[0].stockOnHand").value(40))
        .andExpect(jsonPath("$.content[0].costPrice").value(100.00));
    get(owner, "/api/stock/" + phone.path("id").asLong() + "/serials")
        .andExpect(jsonPath("$.length()").value(2));

    // Owed: 28,000 - 10,000 paid on delivery.
    get(owner, "/api/suppliers/" + supplierId).andExpect(jsonPath("$.balance").value(18000.00));
    post(
            owner,
            "/api/suppliers/" + supplierId + "/payments",
            Map.of("amount", 20000, "method", "CASH"))
        .andExpect(status().isBadRequest());
    post(
            owner,
            "/api/suppliers/" + supplierId + "/payments",
            Map.of("amount", 8000, "method", "BANK", "reference", "EFT123"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance").value(10000.00));
    get(owner, "/api/suppliers/" + supplierId + "/statement")
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[2].balance").value(10000.00));

    // The same IMEI can't be received twice.
    delivery.put(
        "lines",
        List.of(
            line(
                phone.path("id").asLong(),
                phone.path("units").get(0).path("id").asLong(),
                1,
                12000,
                List.of(imei1))));
    delivery.put("amountPaid", 0);
    post(owner, "/api/purchases", delivery).andExpect(status().isConflict());
  }

  @Test
  void sellingAndReturningMoveStockAndOnlyShelfImeisSell() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode phone = createProduct(owner, "Phone", "IMEI", 10000, null);
    long phoneId = phone.path("id").asLong();
    long unit = phone.path("units").get(0).path("id").asLong();
    int n = SEQ.incrementAndGet();
    String onShelf = SerialsTest.imei("35333333" + String.format("%06d", n));
    String unknown = SerialsTest.imei("35444444" + String.format("%06d", n));
    post(
            owner,
            "/api/stock/adjustments",
            Map.of(
                "productId",
                phoneId,
                "quantity",
                1,
                "reason",
                "OPENING_STOCK",
                "serials",
                List.of(onShelf)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockOnHand").value(1));

    post(cashier, "/api/sales", cashSale(phoneId, unit, 1, 10000, List.of(unknown)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not in stock")));
    JsonNode sale =
        body(
            post(cashier, "/api/sales", cashSale(phoneId, unit, 1, 10000, List.of(onShelf)))
                .andExpect(status().isCreated()));
    get(owner, "/api/stock/" + phoneId + "/serials").andExpect(jsonPath("$.length()").value(0));

    post(
            owner,
            "/api/sales/" + sale.path("id").asLong() + "/returns",
            Map.of(
                "lines",
                List.of(
                    Map.of(
                        "saleLineId",
                        sale.path("lines").get(0).path("id").asLong(),
                        "quantity",
                        1)),
                "reason",
                "Changed mind",
                "refundMethod",
                "CASH"))
        .andExpect(status().isCreated());
    get(owner, "/api/stock/" + phoneId + "/serials").andExpect(jsonPath("$[0]").value(onShelf));
    get(owner, "/api/stock/" + phoneId + "/history")
        .andExpect(jsonPath("$.content[0].type").value("RETURN"))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(1))
        .andExpect(jsonPath("$.content[1].type").value("SALE"))
        .andExpect(jsonPath("$.content[1].balanceAfter").value(0));

    // Cashiers can read IMEIs on the shelf but not manage stock.
    get(cashier, "/api/stock/" + phoneId + "/serials").andExpect(status().isOk());
    get(cashier, "/api/stock").andExpect(status().isForbidden());
  }

  @Test
  void lowStockAlertAndPartsUsedForWarrantyRepairs() throws Exception {
    String owner = owner();
    JsonNode part = createProduct(owner, "Screen part", "NONE", 1500, null);
    long partId = part.path("id").asLong();
    Map<String, Object> edit = json.convertValue(part, Map.class);
    edit.put("reorderLevel", 3);
    put(owner, "/api/products/" + partId, edit).andExpect(status().isOk());
    post(
            owner,
            "/api/stock/adjustments",
            Map.of("productId", partId, "quantity", 5, "reason", "OPENING_STOCK"))
        .andExpect(status().isOk());
    post(
            owner,
            "/api/stock/adjustments",
            Map.of(
                "productId",
                partId,
                "quantity",
                -2,
                "reason",
                "WARRANTY_REPAIR",
                "note",
                "Replaced screen on TG000123"))
        .andExpect(jsonPath("$.stockOnHand").value(3));

    JsonNode low = body(get(owner, "/api/stock?low=true&size=100"));
    assertThat(low.path("content").findValuesAsText("name")).contains(part.path("name").asText());
    get(owner, "/api/stock/summary")
        .andExpect(
            jsonPath("$.lowStockCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    get(owner, "/api/stock/" + partId + "/history")
        .andExpect(jsonPath("$.content[0].reason").value("WARRANTY_REPAIR"))
        .andExpect(jsonPath("$.content[0].note").value("Replaced screen on TG000123"));
  }

  @Test
  void weeklyCountSetsStockToWhatWasCounted() throws Exception {
    String owner = owner();
    JsonNode item = createProduct(owner, "Earphones", "NONE", 500, null);
    long id = item.path("id").asLong();
    String category = item.path("category").asText();
    post(
            owner,
            "/api/stock/adjustments",
            Map.of("productId", id, "quantity", 10, "reason", "OPENING_STOCK"))
        .andExpect(status().isOk());

    JsonNode count =
        body(
            post(owner, "/api/stock/counts", Map.of("category", category))
                .andExpect(status().isCreated()));
    long countId = count.path("id").asLong();
    post(owner, "/api/stock/counts", Map.of()).andExpect(status().isConflict());

    put(owner, "/api/stock/counts/" + countId, List.of(Map.of("productId", id, "counted", 8)))
        .andExpect(jsonPath("$.countedProducts").value(1));
    post(owner, "/api/stock/counts/" + countId + "/finish", Map.of())
        .andExpect(jsonPath("$.status").value("FINISHED"))
        .andExpect(jsonPath("$.differenceValue").value(0.00));
    get(owner, "/api/stock?q=" + number(item))
        .andExpect(jsonPath("$.content[0].stockOnHand").value(8));
    get(owner, "/api/stock/summary").andExpect(jsonPath("$.lastCountAt").isNotEmpty());
  }
}
