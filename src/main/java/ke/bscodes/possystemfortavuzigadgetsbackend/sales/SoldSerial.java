package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** An IMEI or serial number sold on a sale line; used for warranty checks. */
@Entity
@Table(name = "sold_serials")
public class SoldSerial {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_line_id")
  private SaleLine saleLine;

  private String serial;
  private Instant returnedAt;

  protected SoldSerial() {}

  SoldSerial(SaleLine saleLine, String serial) {
    this.saleLine = saleLine;
    this.serial = serial;
  }

  public void markReturned(Instant now) {
    this.returnedAt = now;
  }

  public String getSerial() {
    return serial;
  }

  public Instant getReturnedAt() {
    return returnedAt;
  }
}
