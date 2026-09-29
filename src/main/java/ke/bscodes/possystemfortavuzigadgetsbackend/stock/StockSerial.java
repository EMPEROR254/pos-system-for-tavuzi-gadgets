package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A phone/laptop/TV known by IMEI or serial number, and whether it is on the shelf. */
@Entity
@Table(name = "stock_serials")
public class StockSerial {

  public enum Status {
    IN_STOCK,
    SOLD,
    REMOVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long productId;
  private String serial;

  @Enumerated(EnumType.STRING)
  private Status status;

  private Instant createdAt;
  private Instant updatedAt;

  protected StockSerial() {}

  public StockSerial(long productId, String serial, Instant now) {
    this.productId = productId;
    this.serial = serial;
    this.status = Status.IN_STOCK;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void moveTo(Status status, long productId, Instant now) {
    this.status = status;
    this.productId = productId;
    this.updatedAt = now;
  }

  public Long getProductId() {
    return productId;
  }

  public String getSerial() {
    return serial;
  }

  public Status getStatus() {
    return status;
  }
}
