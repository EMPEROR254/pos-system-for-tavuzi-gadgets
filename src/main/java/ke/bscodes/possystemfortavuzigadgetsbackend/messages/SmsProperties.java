package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS settings (application.yml "app.sms"). With no API key, messages are only logged - handy in
 * development and until the shop opens an Africa's Talking account. The key is a secret: it comes
 * from AT_API_KEY in .env, never from a file in git.
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
    String username,
    String apiKey,
    String senderId,
    boolean sandbox,
    String promoFooter,
    boolean dispatchEnabled,
    String callbackToken,
    LocalTime quietStart,
    LocalTime quietEnd) {

  public SmsProperties {
    quietStart = quietStart == null ? LocalTime.of(20, 0) : quietStart;
    quietEnd = quietEnd == null ? LocalTime.of(8, 0) : quietEnd;
  }

  public QuietHours quietHours() {
    return new QuietHours(quietStart, quietEnd);
  }

  /** Delivery reports and replies are only accepted when a callback token is set. */
  public boolean callbacksEnabled() {
    return callbackToken != null && callbackToken.length() >= 16;
  }

  public boolean live() {
    return apiKey != null && !apiKey.isBlank() && username != null && !username.isBlank();
  }

  public String baseUrl() {
    return sandbox ? "https://api.sandbox.africastalking.com" : "https://api.africastalking.com";
  }
}
