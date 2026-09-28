package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The thermal receipt printer on the shop network (optional: without it staff print from the
 * browser). width is characters per line: 48 for 80mm paper, 32 for 58mm.
 */
@ConfigurationProperties(prefix = "app.printer")
public record PrinterProperties(String host, Integer port, Integer width) {

  public boolean configured() {
    return host != null && !host.isBlank();
  }

  public int portOrDefault() {
    return port == null ? 9100 : port;
  }

  public int widthOrDefault() {
    return width == null ? 48 : width;
  }
}
