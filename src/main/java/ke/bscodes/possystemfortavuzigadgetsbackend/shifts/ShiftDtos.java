package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Request and response shapes for /api/shifts. */
public final class ShiftDtos {

  private ShiftDtos() {}

  public record OpenRequest(
      @NotNull(message = "Count the float in the drawer.")
          @DecimalMin(value = "0", message = "Can't be negative.")
          @DecimalMax(value = "1000000", message = "That is too much for a float.")
          @Digits(integer = 10, fraction = 2)
          BigDecimal openingFloat) {}

  public record CloseRequest(
      @NotNull(message = "Count the cash in the drawer.")
          @DecimalMin(value = "0", message = "Can't be negative.")
          @DecimalMax(value = "100000000", message = "That is too much.")
          @Digits(integer = 10, fraction = 2)
          BigDecimal countedCash,
      @Size(max = 300, message = "At most 300 characters.") String note) {}

  /**
   * The cashier's open shift. Deliberately without the expected cash: the count at closing is
   * "blind", so the cashier counts what is really there.
   */
  public record CurrentShift(long id, Instant openedAt, BigDecimal openingFloat, long salesCount) {}

  /** A shift for the owner's cash-up list; the money fields are null while it is open. */
  public record ShiftView(
      long id,
      long cashierId,
      String cashierName,
      Instant openedAt,
      Instant closedAt,
      String closedByName,
      BigDecimal openingFloat,
      BigDecimal cashSales,
      BigDecimal cashRefunds,
      BigDecimal expectedCash,
      BigDecimal countedCash,
      BigDecimal difference,
      String note) {}

  /** Shown to the cashier after closing: the count against what was expected. */
  public record CloseResult(ShiftView shift, long salesCount, BigDecimal mpesa, BigDecimal card) {}
}
