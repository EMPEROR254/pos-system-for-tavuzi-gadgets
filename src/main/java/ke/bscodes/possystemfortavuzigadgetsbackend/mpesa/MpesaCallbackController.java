package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives STK Push results from Safaricom: POST /api/mpesa/callback. This endpoint must be public
 * (permit it in the security configuration) and reachable over HTTPS. It always answers "Accepted"
 * quickly.
 */
@RestController
@RequestMapping("/api/mpesa")
public class MpesaCallbackController {

  private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);
  private final ObjectProvider<MpesaPaymentHandler> handler;

  public MpesaCallbackController(ObjectProvider<MpesaPaymentHandler> handler) {
    this.handler = handler;
  }

  @PostMapping("/callback")
  public Map<String, Object> callback(@RequestBody JsonNode body) {
    StkCallback result = StkCallback.parse(body);
    log.info(
        "M-Pesa result {} for {}: {}",
        result.resultCode(),
        result.checkoutRequestId(),
        result.resultDesc());
    MpesaPaymentHandler paymentHandler = handler.getIfAvailable();
    if (paymentHandler == null) {
      log.warn("No MpesaPaymentHandler bean - the payment result was not recorded");
    } else {
      paymentHandler.onStkResult(result);
    }
    return Map.of("ResultCode", 0, "ResultDesc", "Accepted");
  }
}
