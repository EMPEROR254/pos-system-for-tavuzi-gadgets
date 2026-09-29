package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends SMS through Africa's Talking (the usual SMS gateway in Kenya). Recipient status codes
 * 100-102 mean accepted; anything else is a failure that the dispatcher may retry.
 */
public class AfricasTalkingSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(AfricasTalkingSmsSender.class);

  private final RestClient http;
  private final SmsProperties props;

  public AfricasTalkingSmsSender(RestClient.Builder builder, SmsProperties props) {
    this.http = builder.baseUrl(props.baseUrl()).build();
    this.props = props;
  }

  @Override
  public Result send(String phone, String message) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("username", props.username());
    form.add("to", "+" + phone);
    form.add("message", message);
    if (props.senderId() != null && !props.senderId().isBlank()) {
      form.add("from", props.senderId());
    }
    try {
      JsonNode body =
          http.post()
              .uri("/version1/messaging")
              .header("apiKey", props.apiKey())
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JsonNode.class);
      JsonNode recipient =
          body == null ? null : body.path("SMSMessageData").path("Recipients").path(0);
      if (recipient == null || recipient.isMissingNode()) {
        return Result.failed("No recipient in the reply");
      }
      int code = recipient.path("statusCode").asInt();
      if (code >= 100 && code <= 102) {
        return Result.ok(recipient.path("messageId").asText(null));
      }
      return Result.failed(recipient.path("status").asText("Rejected") + " (" + code + ")");
    } catch (RestClientException e) {
      log.warn("SMS to {} failed: {}", LoggingSmsSender.mask(phone), e.getMessage());
      return Result.failed("Could not reach the SMS provider");
    }
  }
}
