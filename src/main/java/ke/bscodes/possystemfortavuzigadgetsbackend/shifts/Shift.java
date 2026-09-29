package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cashier's shift: opened with a float, closed with a count of the cash in the drawer. The
 * expected cash and the difference are fixed at closing, so later changes can't alter a cash-up.
 */
@Entity
@Table(name = "shifts")
public class Shift {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long cashierId;
  private Instant openedAt;
  private BigDecimal openingFloat;
  private Instant closedAt;
  private Long closedBy;
  private BigDecimal cashSales;
  private BigDecimal cashRefunds;
  private BigDecimal expectedCash;
  private BigDecimal countedCash;
  private BigDecimal difference;
  private String note;

  protected Shift() {}

  public Shift(long cashierId, BigDecimal openingFloat, Instant now) {
    this.cashierId = cashierId;
    this.openingFloat = openingFloat;
    this.openedAt = now;
  }

  /** Closes the shift: over (+) or short (-) is what was counted minus what should be there. */
  public void close(ShiftCash cash, BigDecimal counted, String note, long closedBy, Instant now) {
    this.cashSales = cash.cashSales();
    this.cashRefunds = cash.cashRefunds();
    this.expectedCash = cash.expected(openingFloat);
    this.countedCash = counted;
    this.difference = counted.subtract(expectedCash);
    this.note = note;
    this.closedBy = closedBy;
    this.closedAt = now;
  }

  public boolean isOpen() {
    return closedAt == null;
  }

  public Long getId() {
    return id;
  }

  public Long getCashierId() {
    return cashierId;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public BigDecimal getOpeningFloat() {
    return openingFloat;
  }

  public Instant getClosedAt() {
    return closedAt;
  }
}
