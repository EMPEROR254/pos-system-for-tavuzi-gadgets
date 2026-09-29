package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Customers and messages end to end: the till records customers by phone, SMS receipts are queued
 * and sent in the background, and promotions only reach customers who agreed to them.
 */
class CustomersAndMessagesFlowTest extends ApiTest {

  @Autowired private MessageService messages;

  private void sendQueued() {
    messages.queued().forEach(messages::sendOne);
  }

  private String phone() {
    return "0799" + String.format("%06d", SEQ.incrementAndGet());
  }

  @Test
  void tillRecordsTheCustomerAndSendsAnSmsReceipt() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode cable = createProduct(owner, "Charger", "NONE", 650, null);
    String phone = phone();

    Map<String, Object> sale =
        cashSale(
            cable.path("id").asLong(),
            cable.path("units").get(0).path("id").asLong(),
            2,
            1300,
            null);
    sale.put("customerName", "Grace Chebet");
    sale.put("customerPhone", phone);
    sale.put("marketingOptIn", true);
    sale.put("smsReceipt", true);
    long saleId =
        body(post(cashier, "/api/sales", sale)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").value("254" + phone.substring(1))))
            .path("id")
            .asLong();

    // The till can find the customer again by typing part of the phone number.
    JsonNode found = body(get(cashier, "/api/customers/lookup?q=" + phone.substring(1)));
    org.assertj.core.api.Assertions.assertThat(found).hasSize(1);
    long customerId = found.get(0).path("id").asLong();
    get(cashier, "/api/customers/lookup?q=" + phone)
        .andExpect(jsonPath("$[0].name").value("Grace Chebet"))
        .andExpect(jsonPath("$[0].marketingOptIn").value(true));

    // Owner/manager see purchases and totals; cashiers can't open the full list.
    get(owner, "/api/customers/" + customerId)
        .andExpect(jsonPath("$.visits").value(1))
        .andExpect(jsonPath("$.totalSpent").value(1300.00));
    get(owner, "/api/customers/" + customerId + "/sales")
        .andExpect(jsonPath("$.content[0].id").value(saleId));
    get(cashier, "/api/customers").andExpect(status().isForbidden());

    // SMS receipt: queued at checkout, sent in the background.
    get(owner, "/api/messages?kind=RECEIPT&status=QUEUED&size=100")
        .andExpect(
            jsonPath("$.content[?(@.phone == '254%s')]".formatted(phone.substring(1)))
                .isNotEmpty());
    sendQueued();
    get(owner, "/api/messages?kind=RECEIPT&size=5")
        .andExpect(jsonPath("$.content[0].status").value("SENT"))
        .andExpect(
            jsonPath("$.content[0].body").value(org.hamcrest.Matchers.containsString("KES 1,300")));

    // Resend from the receipt screen.
    post(cashier, "/api/sales/" + saleId + "/sms-receipt", Map.of())
        .andExpect(status().isAccepted());
  }

  @Test
  void promotionsOnlyReachCustomersWhoAgreed() throws Exception {
    String owner = owner();
    String manager = newUser("MANAGER");
    String cashier = newUser("CASHIER");
    String agreed = phone();
    String notAgreed = phone();
    post(
            cashier,
            "/api/customers",
            Map.of("name", "Agreed " + agreed, "phone", agreed, "marketingOptIn", true))
        .andExpect(status().isCreated());
    post(
            cashier,
            "/api/customers",
            Map.of("name", "No " + notAgreed, "phone", notAgreed, "marketingOptIn", false))
        .andExpect(status().isCreated());
    post(cashier, "/api/customers", Map.of("name", "Duplicate", "phone", agreed))
        .andExpect(status().isConflict());

    Map<String, Object> promo = Map.of("message", "Tecno Spark 20 now KES 15,999 at Resma Plaza!");
    post(cashier, "/api/messages/campaigns/preview", promo).andExpect(status().isForbidden());
    JsonNode preview =
        body(post(manager, "/api/messages/campaigns/preview", promo).andExpect(status().isOk()));
    int recipients = preview.path("recipients").asInt();

    post(manager, "/api/messages/campaigns", promo)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recipients").value(recipients))
        .andExpect(jsonPath("$.smsParts").value(1));
    JsonNode queued = body(get(owner, "/api/messages?kind=PROMO&status=QUEUED&size=100"));
    String phones = queued.path("content").findValuesAsText("phone").toString();
    org.assertj.core.api.Assertions.assertThat(phones)
        .contains("254" + agreed.substring(1))
        .doesNotContain("254" + notAgreed.substring(1));
    org.assertj.core.api.Assertions.assertThat(queued.path("content").get(0).path("body").asText())
        .endsWith("Reply STOP to opt out.");
    sendQueued();
  }
}
