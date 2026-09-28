package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M-Pesa Daraja settings, read from application.yml under "mpesa" (values come
 * from environment variables - never commit real keys). Shared module from BS Codes.
 *
 * <pre>
 * mpesa:
 *   environment: ${MPESA_ENVIRONMENT:sandbox}
 *   consumer-key: ${MPESA_CONSUMER_KEY}
 *   consumer-secret: ${MPESA_CONSUMER_SECRET}
 *   shortcode: ${MPESA_SHORTCODE}
 *   passkey: ${MPESA_PASSKEY}
 *   callback-url: ${MPESA_CALLBACK_URL}
 * </pre>
 */
@ConfigurationProperties(prefix = "mpesa")
public record MpesaProperties(
    String environment,
    String consumerKey,
    String consumerSecret,
    String shortcode,
    String passkey,
    String callbackUrl,
    String transactionType,
    String partyB) {

  public String baseUrl() {
    return "production".equalsIgnoreCase(environment)
        ? "https://api.safaricom.co.ke"
        : "https://sandbox.safaricom.co.ke";
  }

  /** Paybill: CustomerPayBillOnline (default). Till: CustomerBuyGoodsOnline. */
  public String transactionTypeOrDefault() {
    return transactionType == null || transactionType.isBlank()
        ? "CustomerPayBillOnline"
        : transactionType;
  }

  /** The till number for Buy Goods, otherwise the shortcode. */
  public String partyBOrShortcode() {
    return partyB == null || partyB.isBlank() ? shortcode : partyB;
  }
}
