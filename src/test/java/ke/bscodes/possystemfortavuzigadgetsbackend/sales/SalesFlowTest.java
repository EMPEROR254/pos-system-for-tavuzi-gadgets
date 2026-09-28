package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.SerialsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Selling, payments, tax and receipts end to end against PostgreSQL: products with units and
 * prices, checkout with split payments, discounts with approval, IMEIs and warranty, receipts, and
 * owner-approved returns.
 */
@AutoConfigureMockMvc
class SalesFlowTest extends IntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(100);

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  private String owner;
  private String manager;
  private String cashier;
  private String cashierName;
  private long phoneId;
  private long phoneUnit;
  private long cableId;
  private long cablePiece;
  private long cableCarton;

  @BeforeEach
  void setUp() throws Exception {
    int n = SEQ.incrementAndGet();
    owner = login("owner", "owner-pass-123");
    createUser("mgr" + n, "MANAGER", "manager-pass-" + n);
    cashierName = "cash" + n;
    createUser(cashierName, "CASHIER", "cashier-pass-" + n);
    manager = login("mgr" + n, "manager-pass-" + n);
    cashier = login(cashierName, "cashier-pass-" + n);

    JsonNode phone =
        body(
            send(
                    owner,
                    "/api/products",
                    Map.of(
                        "name",
                        "Tecno Spark 20 #" + n,
                        "sku",
                        "TS20-" + n,
                        "category",
                        "Phones",
                        "vatCategory",
                        "STANDARD",
                        "serialType",
                        "IMEI",
                        "warrantyMonths",
                        12,
                        "costPrice",
                        9000,
                        "active",
                        true,
                        "units",
                        List.of(
                            Map.of(
                                "name",
                                "Piece",
                                "factor",
                                1,
                                "retailPrice",
                                11600,
                                "wholesalePrice",
                                11000))))
                .andExpect(status().isCreated()));
    phoneId = phone.path("id").asLong();
    phoneUnit = phone.path("units").get(0).path("id").asLong();

    JsonNode cable =
        body(
            send(
                    owner,
                    "/api/products",
                    Map.of(
                        "name",
                        "USB-C cable #" + n,
                        "vatCategory",
                        "EXEMPT",
                        "serialType",
                        "NONE",
                        "warrantyMonths",
                        0,
                        "costPrice",
                        80,
                        "active",
                        true,
                        "units",
                        List.of(
                            Map.of(
                                "name",
                                "Piece",
                                "factor",
                                1,
                                "barcode",
                                "CBL" + n,
                                "retailPrice",
                                200),
                            Map.of(
                                "name",
                                "Carton of 20",
                                "factor",
                                20,
                                "retailPrice",
                                3600,
                                "wholesalePrice",
                                3000))))
                .andExpect(status().isCreated()));
    cableId = cable.path("id").asLong();
    cablePiece = cable.path("units").get(0).path("id").asLong();
    cableCarton = cable.path("units").get(1).path("id").asLong();
  }

  @Test
  void cashierFindsProductsByBarcodeButCannotEditThem() throws Exception {
    int n = SEQ.get();
    mvc.perform(
            get("/api/products/lookup")
                .param("q", "CBL" + n)
                .header("Authorization", bearer(cashier)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(cableId))
        .andExpect(jsonPath("$[0].costPrice").doesNotExist());
    mvc.perform(get("/api/products").header("Authorization", bearer(cashier)))
        .andExpect(status().isForbidden());
  }

  @Test
  void splitPaymentSaleWithImeiReceiptAndWarranty() throws Exception {
    String imei = SerialsTest.imei("3569380356" + String.format("%04d", SEQ.get()));
    String code = mpesaCode();
    String clientRef = UUID.randomUUID().toString();
    Map<String, Object> sale =
        checkoutBody(
            clientRef,
            "RETAIL",
            List.of(
                line(phoneId, phoneUnit, 1, null, List.of(imei)),
                line(cableId, cablePiece, 2, null, null)),
            List.of(pay("MPESA_TILL", 10000, code.toLowerCase()), pay("CASH", 2500, null)));

    JsonNode saved =
        body(
            send(cashier, "/api/sales", sale)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(12000.00))
                .andExpect(jsonPath("$.vatTotal").value(1600.00))
                .andExpect(jsonPath("$.changeDue").value(500.00))
                .andExpect(jsonPath("$.payments[0].reference").value(code))
                .andExpect(jsonPath("$.lines[0].serials[0].serial").value(imei))
                .andExpect(jsonPath("$.lines[0].vatCode").value("B"))
                .andExpect(jsonPath("$.lines[1].vatCode").value("A")));
    long saleId = saved.path("id").asLong();
    String receiptNo = saved.path("receiptNo").asText();
    assertThat(receiptNo).startsWith("TG");

    // Sending the same sale again (double tap) returns the first sale.
    send(cashier, "/api/sales", sale)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.receiptNo").value(receiptNo));

    // Receipt: VAT per code, return policy, till/paybill details.
    mvc.perform(get("/api/sales/" + saleId + "/receipt").header("Authorization", bearer(cashier)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shopName").value("TAVUZI GADGETS"))
        .andExpect(jsonPath("$.returnPolicy").exists())
        .andExpect(jsonPath("$.vatBreakdown.length()").value(2))
        .andExpect(jsonPath("$.vatBreakdown[1].code").value("B"))
        .andExpect(jsonPath("$.vatBreakdown[1].vat").value(1600.00));
    // No printer configured in tests: a clear message instead of an error.
    send(cashier, "/api/sales/" + saleId + "/receipt/print", Map.of())
        .andExpect(status().isConflict());

    // Warranty check by IMEI.
    mvc.perform(get("/api/warranty").param("q", imei).header("Authorization", bearer(cashier)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].receiptNo").value(receiptNo))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));

    // The same M-Pesa code or the same phone can't be used again.
    send(
            cashier,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "RETAIL",
                List.of(line(cableId, cablePiece, 1, null, null)),
                List.of(pay("MPESA_PAYBILL", 200, code))))
        .andExpect(status().isConflict());
    send(
            cashier,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "RETAIL",
                List.of(line(phoneId, phoneUnit, 1, null, List.of(imei))),
                List.of(pay("CASH", 11600, null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(imei + " was already sold. Check the number."));
  }

  @Test
  void wholesaleCartonSale() throws Exception {
    send(
            cashier,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "WHOLESALE",
                List.of(line(cableId, cableCarton, 2, null, null)),
                List.of(pay("CARD", 6000, "A12345"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].unitName").value("Carton of 20"))
        .andExpect(jsonPath("$.total").value(6000.00))
        .andExpect(jsonPath("$.vatTotal").value(0));
  }

  @Test
  void cashierDiscountNeedsASupervisor() throws Exception {
    int n = SEQ.get();
    var discounted = List.of(line(cableId, cablePiece, 1, 50, null));
    send(
            cashier,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "RETAIL",
                discounted,
                List.of(pay("CASH", 150, null))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("A discount needs approval from a supervisor."));

    Map<String, Object> approved =
        checkoutBody(
            UUID.randomUUID().toString(), "RETAIL", discounted, List.of(pay("CASH", 150, null)));
    approved.put("approval", Map.of("username", "mgr" + n, "password", "manager-pass-" + n));
    send(cashier, "/api/sales", approved)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.discountTotal").value(50.00))
        .andExpect(jsonPath("$.total").value(150.00));

    // Managers give discounts themselves.
    send(
            manager,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "RETAIL",
                discounted,
                List.of(pay("CASH", 150, null))))
        .andExpect(status().isCreated());
  }

  @Test
  void returnsNeedTheOwnerAndFreeTheImei() throws Exception {
    int n = SEQ.get();
    String imei = SerialsTest.imei("8612340000" + String.format("%04d", n));
    JsonNode sale =
        body(
            send(
                    cashier,
                    "/api/sales",
                    checkoutBody(
                        UUID.randomUUID().toString(),
                        "RETAIL",
                        List.of(
                            line(phoneId, phoneUnit, 1, null, List.of(imei)),
                            line(cableId, cablePiece, 3, null, null)),
                        List.of(pay("CASH", 12200, null))))
                .andExpect(status().isCreated()));
    long saleId = sale.path("id").asLong();
    long phoneLine = sale.path("lines").get(0).path("id").asLong();
    long cableLine = sale.path("lines").get(1).path("id").asLong();

    Map<String, Object> ret =
        new java.util.HashMap<>(
            Map.of(
                "lines", List.of(Map.of("saleLineId", phoneLine, "quantity", 1)),
                "reason", "Screen fault on day one",
                "refundMethod", "CASH"));
    send(cashier, "/api/sales/" + saleId + "/returns", ret).andExpect(status().isForbidden());
    // A manager can't approve returns - only the owner (spec).
    ret.put("approval", Map.of("username", "mgr" + n, "password", "manager-pass-" + n));
    send(cashier, "/api/sales/" + saleId + "/returns", ret).andExpect(status().isForbidden());
    ret.put("approval", Map.of("username", "owner", "password", "owner-pass-123"));
    send(cashier, "/api/sales/" + saleId + "/returns", ret)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.refundAmount").value(11600.00))
        .andExpect(jsonPath("$.vatAmount").value(1600.00))
        .andExpect(jsonPath("$.status").value("PARTLY_RETURNED"));

    // Can't return more than was sold.
    send(
            owner,
            "/api/sales/" + saleId + "/returns",
            Map.of(
                "lines",
                List.of(Map.of("saleLineId", cableLine, "quantity", 4)),
                "reason",
                "x",
                "refundMethod",
                "CASH"))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/warranty").param("q", imei).header("Authorization", bearer(owner)))
        .andExpect(jsonPath("$[0].status").value("RETURNED"));
    // The returned phone can be sold again.
    send(
            cashier,
            "/api/sales",
            checkoutBody(
                UUID.randomUUID().toString(),
                "RETAIL",
                List.of(line(phoneId, phoneUnit, 1, null, List.of(imei))),
                List.of(pay("CASH", 11600, null))))
        .andExpect(status().isCreated());
  }

  @Test
  void cashiersSeeOnlyTheirOwnSales() throws Exception {
    JsonNode managerSale =
        body(
            send(
                    manager,
                    "/api/sales",
                    checkoutBody(
                        UUID.randomUUID().toString(),
                        "RETAIL",
                        List.of(line(cableId, cablePiece, 1, null, null)),
                        List.of(pay("CASH", 200, null))))
                .andExpect(status().isCreated()));
    mvc.perform(
            get("/api/sales/" + managerSale.path("id").asLong())
                .header("Authorization", bearer(cashier)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/sales").header("Authorization", bearer(cashier)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.cashierName != '%s')]".formatted("Test " + cashierName))
                .isEmpty());
    mvc.perform(
            get("/api/sales")
                .param("receipt", managerSale.path("receiptNo").asText())
                .header("Authorization", bearer(owner)))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  // ---- helpers ----

  private static Map<String, Object> line(
      long product, long unit, int qty, Integer discount, List<String> serials) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("productId", product);
    m.put("unitId", unit);
    m.put("quantity", qty);
    m.put("discount", discount);
    m.put("serials", serials);
    return m;
  }

  private static Map<String, Object> pay(String method, int amount, String reference) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("method", method);
    m.put("amount", amount);
    m.put("reference", reference);
    return m;
  }

  private static Map<String, Object> checkoutBody(
      String clientRef, String tier, List<?> lines, List<?> payments) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("clientRef", clientRef);
    m.put("priceTier", tier);
    m.put("lines", lines);
    m.put("payments", payments);
    return m;
  }

  private static String mpesaCode() {
    return ("SK" + UUID.randomUUID().toString().replace("-", "")).substring(0, 10).toUpperCase();
  }

  private void createUser(String username, String role, String password) throws Exception {
    send(
            owner,
            "/api/users",
            Map.of(
                "username",
                username,
                "fullName",
                "Test " + username,
                "role",
                role,
                "password",
                password))
        .andExpect(status().isCreated());
  }

  private String login(String username, String password) throws Exception {
    return body(mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", password))))
            .andExpect(status().isOk()))
        .path("accessToken")
        .asText();
  }

  private ResultActions send(String token, String path, Object body) throws Exception {
    return mvc.perform(
        post(path)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
