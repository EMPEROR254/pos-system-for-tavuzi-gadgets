package ke.bscodes.possystemfortavuzigadgetsbackend.hardware;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Receipt printer settings. host is the printer's IP on the shop network (empty = print from the
 * browser); width is characters per line: 48 for 80mm paper, 32 for 58mm, 42 for some 80mm models.
 */
public record PrinterConfig(
    @Size(max = 100)
        @Pattern(
            regexp = "^$|^[A-Za-z0-9.\\-]+$",
            message = "Enter the printer's IP address, e.g. 192.168.1.50.")
        String host,
    @Min(value = 1, message = "1-65535.") @Max(value = 65535, message = "1-65535.") int port,
    int width,
    boolean openDrawer,
    boolean autoPrint) {

  public boolean configured() {
    return host != null && !host.isBlank();
  }
}
