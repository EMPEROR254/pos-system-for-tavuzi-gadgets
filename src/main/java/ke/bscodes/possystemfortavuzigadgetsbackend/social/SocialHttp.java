package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Sends a request and returns the JSON answer, turning HTTP errors into SocialApiException. */
final class SocialHttp {

  private SocialHttp() {}

  static JsonNode send(RestClient.RequestHeadersSpec<?> request, String network) {
    try {
      return request.exchange(
          (req, res) -> {
            JsonNode body = res.bodyTo(JsonNode.class);
            body = body == null ? NullNode.getInstance() : body;
            int status = res.getStatusCode().value();
            if (status >= 200 && status < 300) {
              return body;
            }
            // 429 (too many requests) and 5xx: the network is busy - try again later.
            boolean retry = status == 429 || status >= 500;
            throw new SocialApiException(network + ": " + message(body, status), retry);
          });
    } catch (ResourceAccessException e) {
      throw new SocialApiException(network + " could not be reached. Will try again.", true);
    }
  }

  /** Meta: {"error":{"message"}}; TikTok: {"error":{"message"}} or {"error_description"}. */
  static String message(JsonNode body, int status) {
    JsonNode error = body.path("error");
    if (error.hasNonNull("message") && !error.path("message").asText().isBlank()) {
      return error.path("message").asText();
    }
    if (body.hasNonNull("error_description")) {
      return body.path("error_description").asText();
    }
    return "refused (HTTP " + status + ")";
  }
}
