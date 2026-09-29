package ke.bscodes.possystemfortavuzigadgetsbackend.hardware;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The receipt printer the owner set up on the Hardware screen (a single row, id 1). */
@Entity
@Table(name = "printer_settings")
public class PrinterSettings {

  @Id private Integer id = 1;

  private String host;
  private int port = 9100;
  private int width = 48;
  private boolean openDrawer;
  private boolean autoPrint;
  private Instant updatedAt;

  protected PrinterSettings() {}

  public PrinterSettings(Instant now) {
    this.updatedAt = now;
  }

  public void update(
      String host, int port, int width, boolean openDrawer, boolean autoPrint, Instant now) {
    this.host = host;
    this.port = port;
    this.width = width;
    this.openDrawer = openDrawer;
    this.autoPrint = autoPrint;
    this.updatedAt = now;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public int getWidth() {
    return width;
  }

  public boolean isOpenDrawer() {
    return openDrawer;
  }

  public boolean isAutoPrint() {
    return autoPrint;
  }
}
