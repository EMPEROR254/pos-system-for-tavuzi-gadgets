package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import java.math.BigDecimal;

/**
 * Money taken in a shift. Cash payments are recorded as handed over, so the change given back is
 * taken off; cash refunds came out of the drawer.
 */
public record ShiftCash(
    long salesCount,
    BigDecimal cashPaid,
    BigDecimal changeGiven,
    BigDecimal cashRefunds,
    BigDecimal mpesa,
    BigDecimal card) {

  public BigDecimal cashSales() {
    return cashPaid.subtract(changeGiven);
  }

  /** What the drawer should hold: the float plus cash sales, less cash refunds. */
  public BigDecimal expected(BigDecimal openingFloat) {
    return openingFloat.add(cashSales()).subtract(cashRefunds);
  }
}
