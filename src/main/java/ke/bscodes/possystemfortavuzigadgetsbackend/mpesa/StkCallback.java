package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * The result Safaricom POSTs to the callback URL. The same CheckoutRequestID
 * can arrive more than once - record payments idempotently.
 */
public record StkCallback(
    String merchantRequestId,
    String checkoutRequestId,
    int resultCode,
    String resultDesc,
    BigDecimal amount,
    String receiptNumber,
    String phone,
    String transactionDate) {

  public boolean successful() {
    return resultCode == 0;
  }

  /** Reads {"Body": {"stkCallback": {...}}}. */
  public static StkCallback parse(JsonNode body) {
    JsonNode callback = body.path("Body").path("stkCallback");
    if (callback.isMissingNode()) {
      throw new MpesaException("Not an STK callback");
    }
    BigDecimal amount = null;
    String receipt = null;
    String phone = null;
    String date = null;
    for (JsonNode item : callback.path("CallbackMetadata").path("Item")) {
      JsonNode value = item.path("Value");
      switch (item.path("Name").asText()) {
        case "Amount" -> amount = value.decimalValue();
        case "MpesaReceiptNumber" -> receipt = value.asText();
        case "PhoneNumber" -> phone = value.asText();
        case "TransactionDate" -> date = value.asText();
        default -> {
          // other items are not needed
        }
      }
    }
    return new StkCallback(
        callback.path("MerchantRequestID").asText(),
        callback.path("CheckoutRequestID").asText(),
        callback.path("ResultCode").asInt(),
        callback.path("ResultDesc").asText(),
        amount,
        receipt,
        phone,
        date);
  }
}
