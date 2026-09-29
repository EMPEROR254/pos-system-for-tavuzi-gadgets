package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SMS end to end: scheduled promotions and quiet hours, delivery reports from Africa's Talking,
 * "Reply STOP" opt-outs, and sending failed messages again.
 */
class SmsFlowTest extends ApiTest {

  private static final String CALLBACKS = "/api/sms/callbacks/test-callback-token-123";

  @Autowired private MessageService messages;
  @Autowired private SmsMessageRepository repository;
  @Autowired private TransactionTemplate tx;
  @Autowired private MutableClock clock;

  private void sendQueued() {
    for (List<Long> ids = messages.queued(); !ids.isEmpty(); ids = messages.queued()) {
      ids.forEach(messages::sendOne);
    }
  }

  private String phone() {
    return "0788" + String.format("%06d", SEQ.incrementAndGet());
  }

  private static String e164(String phone) {
    return "254" + phone.substring(1);
  }

  /** 10:00 in Nairobi, outside the quiet hours. */
  private static final Instant MORNING = Instant.parse("2026-09-28T07:00:00Z");

  private Instant before;

  // The clock is shared by every test class: start from a known time and put it back after, so
  // other tests (e.g. M-Pesa reconciliation for 2026-09-28) still see "today".
  @BeforeEach
  void startInTheMorning() {
    before = clock.instant();
    clock.set(MORNING);
  }

  @AfterEach
  void restoreClock() {
    clock.set(before);
  }

  private ResultActions callback(String path, Map<String, String> fields) throws Exception {
    var request =
        MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
    fields.forEach(request::param);
    return mvc.perform(request);
  }

  private JsonNode promoTo(String owner, String phone) throws Exception {
    JsonNode page = body(get(owner, "/api/messages?kind=PROMO&size=5&phone=" + phone));
    for (JsonNode m : page.path("content")) {
      if (m.path("phone").asText().equals(e164(phone))) {
        return m;
      }
    }
    throw new AssertionError("No promotion to " + phone + ": " + page);
  }

  private String optedInCustomer(String owner) throws Exception {
    String phone = phone();
    post(
            owner,
            "/api/customers",
            Map.of("name", "C " + phone, "phone", phone, "marketingOptIn", true))
        .andExpect(status().isCreated());
    return phone;
  }

  private int audience(String owner) throws Exception {
    return body(post(owner, "/api/messages/campaigns/preview", Map.of("message", "Hi")))
        .path("recipients")
        .asInt();
  }

  @Test
  void scheduledPromotionWaitsForItsTimeAndQuietHoursWaitForMorning() throws Exception {
    String owner = owner();
    String phone = optedInCustomer(owner);

    // Scheduled for 14:00 Nairobi: nothing goes out before then.
    Instant at = clock.instant().plus(Duration.ofHours(4));
    long campaignId =
        body(post(
                    owner,
                    "/api/messages/campaigns",
                    Map.of("message", "Sale!", "sendAt", at.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sendsAt").value(at.toString())))
            .path("campaignId")
            .asLong();
    sendQueued();
    assertThat(promoTo(owner, phone).path("status").asText()).isEqualTo("QUEUED");
    clock.advance(Duration.ofHours(4));
    sendQueued();
    owner = owner(); // sign in again: access tokens last 15 minutes
    assertThat(promoTo(owner, phone).path("status").asText()).isEqualTo("SENT");
    get(owner, "/api/messages/campaigns")
        .andExpect(jsonPath("$[?(@.id == %d)].waiting".formatted(campaignId)).value(0));

    // At 21:00 Nairobi a promotion waits until 08:00 the next morning.
    clock.advance(Duration.ofHours(7));
    owner = owner();
    JsonNode late =
        body(
            post(owner, "/api/messages/campaigns", Map.of("message", "Late offer"))
                .andExpect(status().isCreated()));
    Instant sendsAt = Instant.parse(late.path("sendsAt").asText());
    assertThat(sendsAt.atZone(ZoneId.of("Africa/Nairobi")).getHour()).isEqualTo(8);
    assertThat(Duration.between(clock.instant(), sendsAt)).isEqualTo(Duration.ofHours(11));

    // Too far ahead is refused.
    post(
            owner,
            "/api/messages/campaigns",
            Map.of("message", "x", "sendAt", clock.instant().plus(Duration.ofDays(40)).toString()))
        .andExpect(status().isBadRequest());
    clock.advance(Duration.ofHours(11));
    sendQueued();
  }

  @Test
  void deliveryReportsShowOnTheMessageAndTheCampaign() throws Exception {
    String owner = owner();
    String phone = optedInCustomer(owner);
    long campaignId =
        body(post(owner, "/api/messages/campaigns", Map.of("message", "Earphones 20% off")))
            .path("campaignId")
            .asLong();
    sendQueued();
    long id = promoTo(owner, phone).path("id").asLong();
    String providerId = repository.findById(id).orElseThrow().getProviderMessageId();

    callback(
            CALLBACKS + "/delivery",
            Map.of(
                "id",
                providerId,
                "status",
                "Failed",
                "phoneNumber",
                "+" + e164(phone),
                "failureReason",
                "AbsentSubscriber"))
        .andExpect(status().isOk());
    JsonNode m = promoTo(owner, phone);
    assertThat(m.path("deliveryStatus").asText()).isEqualTo("Failed");
    assertThat(m.path("failureReason").asText()).isEqualTo("AbsentSubscriber");
    get(owner, "/api/messages/campaigns")
        .andExpect(jsonPath("$[?(@.id == %d)].failed".formatted(campaignId)).value(1));

    // Not delivered: the owner sends it again, and this time it arrives.
    String cashier = newUser("CASHIER");
    post(cashier, "/api/messages/" + id + "/resend", Map.of()).andExpect(status().isForbidden());
    post(owner, "/api/messages/" + id + "/resend", Map.of()).andExpect(status().isNoContent());
    post(owner, "/api/messages/" + id + "/resend", Map.of()).andExpect(status().isConflict());
    sendQueued();
    String again = repository.findById(id).orElseThrow().getProviderMessageId();
    callback(CALLBACKS + "/delivery", Map.of("id", again, "status", "Success"))
        .andExpect(status().isOk());
    get(owner, "/api/messages/campaigns")
        .andExpect(jsonPath("$[?(@.id == %d)].delivered".formatted(campaignId)).value(1))
        .andExpect(jsonPath("$[?(@.id == %d)].failed".formatted(campaignId)).value(0));

    // Reports for ids we never sent are accepted and ignored.
    callback(CALLBACKS + "/delivery", Map.of("id", "ATXid_unknown", "status", "Success"))
        .andExpect(status().isOk());
  }

  @Test
  void failedSendCanBeSentAgain() throws Exception {
    String owner = owner();
    String phone = optedInCustomer(owner);
    post(owner, "/api/messages/campaigns", Map.of("message", "Hello"))
        .andExpect(status().isCreated());
    long id = promoTo(owner, phone).path("id").asLong();
    tx.executeWithoutResult(
        s -> {
          SmsMessage m = repository.findById(id).orElseThrow();
          for (int i = 0; i < SmsMessage.MAX_ATTEMPTS; i++) {
            m.recordAttempt(SmsSender.Result.failed("Insufficient balance"), clock.instant());
          }
        });
    assertThat(promoTo(owner, phone).path("status").asText()).isEqualTo("FAILED");
    post(owner, "/api/messages/" + id + "/resend", Map.of()).andExpect(status().isNoContent());
    assertThat(promoTo(owner, phone).path("status").asText()).isEqualTo("QUEUED");
  }

  @Test
  void replyingStopEndsPromotionsUntilTheCustomerAgreesAgainAtTheTill() throws Exception {
    String owner = owner();
    String phone = optedInCustomer(owner);
    int before = audience(owner);

    Map<String, String> reply =
        Map.of("from", "+" + e164(phone), "to", "22384", "text", " stop ", "id", "in-" + phone);
    callback(CALLBACKS + "/inbound", reply).andExpect(status().isOk());
    callback(CALLBACKS + "/inbound", reply).andExpect(status().isOk()); // repeated by the provider
    assertThat(audience(owner)).isEqualTo(before - 1);
    get(owner, "/api/customers/lookup?q=" + phone)
        .andExpect(jsonPath("$[0].marketingOptIn").value(false));
    JsonNode replies = body(get(owner, "/api/messages/replies"));
    assertThat(
            replies.path("content").findValuesAsText("phone").stream()
                .filter(p -> p.equals(e164(phone))))
        .hasSize(1);
    assertThat(replies.path("content").get(0).path("optedOut").asBoolean()).isTrue();

    // An ordinary reply is kept but changes nothing.
    callback(
            CALLBACKS + "/inbound",
            Map.of("from", "+" + e164(phone), "text", "Do you have iPhone 15?", "id", "q-" + phone))
        .andExpect(status().isOk());
    assertThat(audience(owner)).isEqualTo(before - 1);

    // The customer agrees again at the till: promotions resume.
    String cashier = newUser("CASHIER");
    JsonNode cable = createProduct(owner, "Cable " + phone, "NONE", 300, null);
    Map<String, Object> sale =
        new HashMap<>(
            cashSale(
                cable.path("id").asLong(),
                cable.path("units").get(0).path("id").asLong(),
                1,
                300,
                null));
    sale.put("customerPhone", phone);
    sale.put("marketingOptIn", true);
    post(cashier, "/api/sales", sale).andExpect(status().isCreated());
    assertThat(audience(owner)).isEqualTo(before);
  }

  @Test
  void networkOptOutAlsoCoversNumbersThatAreNotCustomersYet() throws Exception {
    String owner = owner();
    String phone = phone();
    callback(CALLBACKS + "/opt-out", Map.of("phoneNumber", "+" + e164(phone), "senderId", "TAVUZI"))
        .andExpect(status().isOk());
    int before = audience(owner);
    // Later added at the counter without asking again: still no promotions.
    post(owner, "/api/customers", Map.of("name", "New " + phone, "phone", phone))
        .andExpect(status().isCreated());
    assertThat(audience(owner)).isEqualTo(before);
  }

  @Test
  void callbacksNeedTheSecretAndStaffScreensNeedPermission() throws Exception {
    callback(
            "/api/sms/callbacks/wrong-token-wrong-token/inbound",
            Map.of("from", "+254700000000", "text", "STOP"))
        .andExpect(status().isNotFound());
    // Numbers that are not Kenyan mobiles are ignored, not retried forever.
    callback(CALLBACKS + "/inbound", Map.of("from", "+14155550100", "text", "STOP"))
        .andExpect(status().isOk());

    String cashier = newUser("CASHIER");
    get(cashier, "/api/messages/account").andExpect(status().isForbidden());
    get(cashier, "/api/messages/campaigns").andExpect(status().isForbidden());
    get(owner(), "/api/messages/account")
        .andExpect(jsonPath("$.live").value(false))
        .andExpect(jsonPath("$.quietHours").value("20:00-08:00"));
  }
}
