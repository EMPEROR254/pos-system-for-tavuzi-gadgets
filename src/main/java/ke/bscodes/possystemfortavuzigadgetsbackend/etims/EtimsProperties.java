package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KRA eTIMS settings (application.yml "app.etims"), all from .env. mode OFF keeps invoices queued
 * (nothing is sent) until the shop's eTIMS device is registered with KRA; SANDBOX is KRA's test
 * system, PRODUCTION the real one. cmcKey is a secret KRA issues when the device is initialised.
 */
@ConfigurationProperties(prefix = "app.etims")
public record EtimsProperties(
    Mode mode,
    String baseUrl,
    String tin,
    String branchId,
    String deviceSerial,
    String cmcKey,
    String defaultItemClass,
    boolean dispatchEnabled) {

  public enum Mode {
    OFF,
    SANDBOX,
    PRODUCTION
  }

  public EtimsProperties {
    mode = mode == null ? Mode.OFF : mode;
    branchId = branchId == null || branchId.isBlank() ? "00" : branchId;
  }

  public boolean enabled() {
    return mode != Mode.OFF;
  }

  /** The OSCU API; baseUrl in .env wins (e.g. for a local test server). */
  public String apiUrl() {
    if (baseUrl != null && !baseUrl.isBlank()) {
      return baseUrl;
    }
    return mode == Mode.PRODUCTION
        ? "https://etims-api.kra.go.ke/etims-api"
        : "https://etims-api-sbx.kra.go.ke/etims-api";
  }

  /** Where a customer checks an invoice with KRA (printed as a QR code on the receipt). */
  public String verifyUrl(String receiptSignature) {
    String host =
        mode == Mode.PRODUCTION ? "https://etims.kra.go.ke" : "https://etims-sbx.kra.go.ke";
    return host
        + "/common/link/etims/receipt/indexEtimsReceiptData?Data="
        + tin
        + branchId
        + receiptSignature;
  }
}
