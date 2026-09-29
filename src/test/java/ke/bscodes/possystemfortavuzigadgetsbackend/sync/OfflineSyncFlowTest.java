package ke.bscodes.possystemfortavuzigadgetsbackend.sync;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.SerialsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Offline selling: the till downloads the catalogue, sells without the server, and sends the sale
 * later. It is recorded at the time it really happened, and sending it twice saves it once.
 */
class OfflineSyncFlowTest extends ApiTest {

  @Autowired private MutableClock clock;

  @Test
  void tillCanDownloadEverythingItNeedsToSellOffline() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode phone = createProduct(owner, "Phone", "IMEI", 15000, null);
    String imei = SerialsTest.imei("35999999" + String.format("%06d", SEQ.incrementAndGet()));
    post(
            owner,
            "/api/stock/adjustments",
            Map.of(
                "productId",
                phone.path("id").asLong(),
                "quantity",
                1,
                "reason",
                "OPENING_STOCK",
                "serials",
                List.of(imei)))
        .andExpect(status().isOk());

    JsonNode catalogue = body(get(cashier, "/api/sync/catalogue").andExpect(status().isOk()));
    JsonNode entry = null;
    for (JsonNode p : catalogue.path("products")) {
      if (p.path("id").asLong() == phone.path("id").asLong()) {
        entry = p;
      }
    }
    org.assertj.core.api.Assertions.assertThat(entry).isNotNull();
    org.assertj.core.api.Assertions.assertThat(
            entry.path("units").get(0).path("retailPrice").asInt())
        .isEqualTo(15000);
    org.assertj.core.api.Assertions.assertThat(entry.has("costPrice")).isFalse();
    org.assertj.core.api.Assertions.assertThat(
            catalogue.path("serialsInStock").path(phone.path("id").asText()).get(0).asText())
        .isEqualTo(imei);
    get(cashier, "/api/sync/status").andExpect(jsonPath("$.etimsMode").value("OFF"));
  }

  @Test
  void offlineSaleIsRecordedWhenItHappenedAndOnlyOnce() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode phone = createProduct(owner, "Phone", "IMEI", 15000, null);
    String imei = SerialsTest.imei("35888888" + String.format("%06d", SEQ.incrementAndGet()));
    post(
            owner,
            "/api/stock/adjustments",
            Map.of(
                "productId",
                phone.path("id").asLong(),
                "quantity",
                1,
                "reason",
                "OPENING_STOCK",
                "serials",
                List.of(imei)))
        .andExpect(status().isOk());

    Instant soldAt = clock.instant().minus(Duration.ofHours(3));
    Map<String, Object> sale =
        cashSale(
            phone.path("id").asLong(),
            phone.path("units").get(0).path("id").asLong(),
            1,
            15000,
            List.of(imei));
    sale.put("offlineCreatedAt", soldAt.toString());

    JsonNode first = body(post(cashier, "/api/sales", sale).andExpect(status().isCreated()));
    org.assertj.core.api.Assertions.assertThat(Instant.parse(first.path("createdAt").asText()))
        .isEqualTo(soldAt);
    // Warranty starts on the day of the sale, not the day it synced.
    org.assertj.core.api.Assertions.assertThat(
            first.path("lines").get(0).path("warrantyEndsAt").asText())
        .startsWith("2027-09-28");

    // The till retried (it never saw the answer): the same sale comes back, not a second one.
    post(cashier, "/api/sales", sale)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.receiptNo").value(first.path("receiptNo").asText()));
  }

  @Test
  void veryOldOfflineSalesAreRefused() throws Exception {
    String owner = owner();
    JsonNode item = createProduct(owner, "Cable", "NONE", 200, null);
    Map<String, Object> sale =
        cashSale(
            item.path("id").asLong(), item.path("units").get(0).path("id").asLong(), 1, 200, null);
    sale.put("offlineCreatedAt", clock.instant().minus(Duration.ofDays(8)).toString());
    post(owner, "/api/sales", sale)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("more than 7 days")));
  }
}
