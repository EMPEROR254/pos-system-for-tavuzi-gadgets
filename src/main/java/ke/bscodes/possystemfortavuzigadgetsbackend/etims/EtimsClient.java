package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to KRA's eTIMS OSCU API. Every call sends the shop's tin, branch id and cmcKey as headers
 * and gets back {resultCd, resultMsg, data}; resultCd "000" means success. Network problems throw
 * Unreachable (try again later); anything else KRA says no to throws Rejected.
 */
@Component
public class EtimsClient {

  /** KRA's signature for an invoice - printed on the receipt. */
  public record Signed(
      Long receiptNo,
      Long totalReceiptNo,
      String internalData,
      String signature,
      String scuId,
      String mrcNo,
      String scuDate) {}

  public static class Unreachable extends RuntimeException {
    public Unreachable(String message) {
      super(message);
    }
  }

  public static class Rejected extends RuntimeException {
    public Rejected(String message) {
      super(message);
    }
  }

  static final String OK = "000";

  private final RestClient.Builder builder;
  private final EtimsProperties props;

  public EtimsClient(RestClient.Builder builder, EtimsProperties props) {
    this.builder = builder;
    this.props = props;
  }

  /** Registers (or updates) a product with KRA: POST /items/saveItems. */
  public void saveItem(Map<String, Object> item) {
    call("/items/saveItems", item);
  }

  /** Sends a sale or credit note and returns KRA's signature: POST /trnsSales/saveSales. */
  public Signed saveSale(Map<String, Object> invoice) {
    JsonNode data = call("/trnsSales/saveSales", invoice).path("data");
    return new Signed(
        data.hasNonNull("curRcptNo") ? data.path("curRcptNo").asLong() : null,
        data.hasNonNull("totRcptNo") ? data.path("totRcptNo").asLong() : null,
        data.path("intrlData").asText(null),
        data.path("rcptSign").asText(null),
        data.path("sdcId").asText(null),
        data.path("mrcNo").asText(null),
        data.path("vsdcRcptPbctDate").asText(data.path("sdcDateTime").asText(null)));
  }

  private JsonNode call(String path, Map<String, Object> body) {
    JsonNode reply;
    try {
      reply =
          builder
              .baseUrl(props.apiUrl())
              .build()
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("tin", props.tin())
              .header("bhfId", props.branchId())
              .header("cmcKey", props.cmcKey())
              .body(body)
              .retrieve()
              .onStatus(
                  s -> s.is4xxClientError(),
                  (req, res) -> {
                    throw new Rejected(
                        "KRA refused the request (HTTP " + res.getStatusCode().value() + ")");
                  })
              .body(JsonNode.class);
    } catch (Rejected e) {
      throw e;
    } catch (RestClientException e) {
      throw new Unreachable("Could not reach KRA eTIMS: " + e.getMessage());
    }
    if (reply == null) {
      throw new Unreachable("KRA eTIMS sent an empty reply");
    }
    String code = reply.path("resultCd").asText("");
    if (!OK.equals(code)) {
      throw new Rejected("KRA eTIMS " + code + ": " + reply.path("resultMsg").asText("rejected"));
    }
    return reply;
  }
}
