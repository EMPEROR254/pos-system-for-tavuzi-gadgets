package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * KRA eTIMS end to end against a fake KRA server: sales and returns are queued, sent in the
 * background, signed, and shown on the receipt; KRA being down or refusing an invoice is handled.
 */
class EtimsFlowTest extends ApiTest {

  static final FakeKra KRA = new FakeKra();

  @DynamicPropertySource
  static void etims(DynamicPropertyRegistry registry) {
    registry.add("app.etims.mode", () -> "SANDBOX");
    registry.add("app.etims.base-url", KRA::url);
    registry.add("app.etims.tin", () -> "P051234567X");
    registry.add("app.etims.cmc-key", () -> "test-cmc-key");
  }

  @Autowired private EtimsService etims;
  @Autowired private MutableClock clock;

  private String owner;
  private JsonNode charger;

  @BeforeEach
  void setUp() throws Exception {
    KRA.mode = "ok";
    owner = owner();
    charger = createProduct(owner, "Charger", "NONE", 1160, null);
    sendDue(); // anything left from other tests in this class
    KRA.requests.clear();
  }

  private void sendDue() {
    etims.due().forEach(etims::send);
  }

  private JsonNode sell() throws Exception {
    return body(
        post(
                owner,
                "/api/sales",
                cashSale(
                    charger.path("id").asLong(),
                    charger.path("units").get(0).path("id").asLong(),
                    1,
                    1160,
                    null))
            .andExpect(status().isCreated()));
  }

  @Test
  void saleIsSignedByKraAndTheReceiptShowsIt() throws Exception {
    JsonNode sale = sell();
    long saleId = sale.path("id").asLong();
    get(owner, "/api/sales/" + saleId + "/receipt")
        .andExpect(jsonPath("$.etims.status").value("PENDING"));

    sendDue();

    // The product was registered first, then the invoice sent with the shop's credentials.
    assertThat(KRA.to("saveItems")).hasSize(1);
    FakeKra.Request invoice = KRA.to("saveSales").getFirst();
    assertThat(invoice.tin()).isEqualTo("P051234567X");
    assertThat(invoice.cmcKey()).isEqualTo("test-cmc-key");
    assertThat(invoice.body().path("rcptTyCd").asText()).isEqualTo("S");
    assertThat(invoice.body().path("taxAmtB").decimalValue()).isEqualByComparingTo("160.00");
    assertThat(invoice.body().path("receipt").path("rptNo").asText())
        .isEqualTo(sale.path("receiptNo").asText());

    get(owner, "/api/sales/" + saleId + "/receipt")
        .andExpect(jsonPath("$.etims.status").value("SENT"))
        .andExpect(jsonPath("$.etims.scuId").value("KRACU0100000001"))
        .andExpect(jsonPath("$.etims.signature").isNotEmpty())
        .andExpect(
            jsonPath("$.etims.verifyUrl")
                .value(
                    org.hamcrest.Matchers.startsWith(
                        "https://etims-sbx.kra.go.ke/common/link/etims/receipt/indexEtimsReceiptData?Data=P051234567X00SIGN")));

    // A second sale of the same product doesn't register it again.
    sell();
    sendDue();
    assertThat(KRA.to("saveItems")).hasSize(1);
  }

  @Test
  void returnSendsACreditNoteForTheSale() throws Exception {
    JsonNode sale = sell();
    sendDue();
    long original = KRA.to("saveSales").getFirst().body().path("invcNo").asLong();
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
                "Faulty",
                "refundMethod",
                "CASH"))
        .andExpect(status().isCreated());
    sendDue();
    JsonNode credit = KRA.to("saveSales").getLast().body();
    assertThat(credit.path("rcptTyCd").asText()).isEqualTo("R");
    assertThat(credit.path("orgInvcNo").asLong()).isEqualTo(original);
  }

  @Test
  void whileKraIsDownInvoicesWaitAndGoLater() throws Exception {
    KRA.mode = "down";
    JsonNode sale = sell();
    sendDue();
    get(owner, "/api/etims/invoices?status=PENDING")
        .andExpect(jsonPath("$.content[0].attempts").value(1))
        .andExpect(
            jsonPath("$.content[0].lastError")
                .value(org.hamcrest.Matchers.containsString("Could not reach")));
    // Not retried straight away: KRA hears nothing more until the back-off wait is over...
    int triedWhileDown = KRA.requests.size();
    KRA.mode = "ok";
    sendDue();
    assertThat(KRA.requests).hasSize(triedWhileDown);
    // ...but after the back-off wait.
    clock.advance(Duration.ofMinutes(2));
    sendDue();
    get(owner, "/api/sales/" + sale.path("id").asLong() + "/receipt")
        .andExpect(jsonPath("$.etims.status").value("SENT"));
  }

  @Test
  void refusedInvoiceNeedsAPersonThenCanBeResent() throws Exception {
    KRA.mode = "reject";
    sell();
    sendDue();
    JsonNode refused = body(get(owner, "/api/etims/invoices?status=REJECTED"));
    long id = refused.path("content").get(0).path("id").asLong();
    assertThat(refused.path("content").get(0).path("lastError").asText())
        .contains("Invalid item class code");
    get(owner, "/api/etims/summary")
        .andExpect(jsonPath("$.rejected").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    KRA.mode = "ok";
    post(owner, "/api/etims/invoices/" + id + "/retry", Map.of())
        .andExpect(jsonPath("$.status").value("PENDING"));
    sendDue();
    get(owner, "/api/etims/invoices?status=REJECTED")
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
