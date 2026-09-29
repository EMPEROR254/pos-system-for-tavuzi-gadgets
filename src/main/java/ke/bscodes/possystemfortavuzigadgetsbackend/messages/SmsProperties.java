package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

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
    boolean dispatchEnabled) {

  public boolean live() {
    return apiKey != null && !apiKey.isBlank() && username != null && !username.isBlank();
  }

  public String baseUrl() {
    return sandbox ? "https://api.sandbox.africastalking.com" : "https://api.africastalking.com";
  }
}
