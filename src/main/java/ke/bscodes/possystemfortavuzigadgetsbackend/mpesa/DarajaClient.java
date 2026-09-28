package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends STK Push requests ("Lipa na M-Pesa Online"): the customer gets a PIN prompt on their phone.
 * The result arrives later at the callback URL (see MpesaCallbackController). Shared module from BS
 * Codes - tested; reuse it.
 */
@Component
public class DarajaClient {

  private final RestClient http;
  private final MpesaProperties properties;
  private final Clock clock;
  private String token;
  private Instant tokenExpiresAt = Instant.EPOCH;

  // Two constructors: tell Spring which one to use (the other is for tests).
  @Autowired
  public DarajaClient(RestClient mpesaRestClient, MpesaProperties properties) {
    this(mpesaRestClient, properties, Clock.systemUTC());
  }

  DarajaClient(RestClient http, MpesaProperties properties, Clock clock) {
    this.http = http;
    this.properties = properties;
    this.clock = clock;
  }

  /** OAuth token, cached until one minute before it expires. */
  synchronized String accessToken() {
    Instant now = clock.instant();
    if (token != null && now.plusSeconds(60).isBefore(tokenExpiresAt)) {
      return token;
    }
    String credentials = properties.consumerKey() + ":" + properties.consumerSecret();
    String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    JsonNode body =
        http.get()
            .uri("/oauth/v1/generate?grant_type=client_credentials")
            .header("Authorization", "Basic " + basic)
            .retrieve()
            .body(JsonNode.class);
    if (body == null || !body.hasNonNull("access_token")) {
      throw new MpesaException("M-Pesa did not return an access token");
    }
    token = body.get("access_token").asText();
    tokenExpiresAt = now.plusSeconds(body.path("expires_in").asLong(3599));
    return token;
  }

  /**
   * Asks the customer to pay. Daraja accepts whole shillings only, so the amount is rounded up.
   * accountReference (max 12 chars) shows on the customer's statement.
   */
  public StkPushResult stkPush(
      String phone, BigDecimal amountKes, String accountReference, String description) {
    String timestamp = DarajaPassword.timestamp(clock.instant());
    String msisdn = Phones.normalize(phone);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("BusinessShortCode", properties.shortcode());
    request.put(
        "Password",
        DarajaPassword.password(properties.shortcode(), properties.passkey(), timestamp));
    request.put("Timestamp", timestamp);
    request.put("TransactionType", properties.transactionTypeOrDefault());
    request.put("Amount", amountKes.setScale(0, RoundingMode.CEILING).intValueExact());
    request.put("PartyA", msisdn);
    request.put("PartyB", properties.partyBOrShortcode());
    request.put("PhoneNumber", msisdn);
    request.put("CallBackURL", properties.callbackUrl());
    request.put("AccountReference", truncate(accountReference, 12));
    request.put("TransactionDesc", truncate(description, 13));

    StkPushResult result =
        http.post()
            .uri("/mpesa/stkpush/v1/processrequest")
            .header("Authorization", "Bearer " + accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(StkPushResult.class);
    if (result == null || !"0".equals(result.responseCode())) {
      throw new MpesaException(
          "STK Push was not accepted: "
              + (result == null ? "no response" : result.responseDescription()));
    }
    return result;
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
