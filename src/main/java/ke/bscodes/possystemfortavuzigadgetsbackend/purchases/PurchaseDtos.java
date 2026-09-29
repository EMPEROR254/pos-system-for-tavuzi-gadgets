package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.SupplierPayment.Method;

/** Request and response bodies for /api/suppliers and /api/purchases. */
public final class PurchaseDtos {

  private PurchaseDtos() {}

  public record SupplierRequest(
      @NotBlank(message = "Enter the supplier's name.")
          @Size(max = 120, message = "At most 120 characters.")
          String name,
      @Size(max = 20, message = "Too long for a phone number.") String phone,
      @Email(message = "That email doesn't look right.") @Size(max = 120) String email,
      @Size(max = 300, message = "At most 300 characters.") String notes,
      boolean active) {}

  /** A supplier with what was bought, what was paid and what the shop still owes. */
  public record SupplierView(
      long id,
      String name,
      String phone,
      String email,
      String notes,
      boolean active,
      BigDecimal purchased,
      BigDecimal paid,
      BigDecimal balance) {

    public SupplierView(
        long id,
        String name,
        String phone,
        String email,
        String notes,
        boolean active,
        BigDecimal purchased,
        BigDecimal paid) {
      this(id, name, phone, email, notes, active, purchased, paid, purchased.subtract(paid));
    }
  }

  public record PurchaseLineRequest(
      @NotNull(message = "Choose a product.") Long productId,
      @NotNull(message = "Choose a unit.") Long unitId,
      @Min(value = 1, message = "At least 1.") @Max(value = 100000, message = "Too many.")
          int quantity,
      @NotNull(message = "Enter the cost.")
          @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal unitCost,
      @Size(max = 1000, message = "Too many serial numbers.") List<String> serials) {}

  /** Goods received. amountPaid is what was paid on delivery (0 = all on credit). */
  public record PurchaseRequest(
      @NotNull(message = "Choose the supplier.") Long supplierId,
      @Size(max = 40, message = "At most 40 characters.") String invoiceNo,
      @Size(max = 300, message = "At most 300 characters.") String note,
      @NotEmpty(message = "Add at least one product.") @Size(max = 300) @Valid
          List<PurchaseLineRequest> lines,
      @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal amountPaid,
      Method paymentMethod,
      @Size(max = 40) String paymentReference) {}

  public record PaymentRequest(
      @NotNull(message = "Enter the amount.")
          @DecimalMin(value = "0.01", message = "Must be more than 0.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal amount,
      @NotNull(message = "Choose how it was paid.") Method method,
      @Size(max = 40, message = "At most 40 characters.") String reference) {}

  public record PurchaseLineView(
      long productId,
      String productName,
      String unitName,
      int quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal) {}

  public record PurchaseView(
      long id,
      long supplierId,
      String supplierName,
      String invoiceNo,
      String note,
      BigDecimal total,
      Instant receivedAt,
      List<PurchaseLineView> lines) {}

  public record PurchaseSummary(
      long id, String supplierName, String invoiceNo, BigDecimal total, Instant receivedAt) {}

  /** One row of a supplier statement: a delivery (+) or a payment (-), with the running balance. */
  public record StatementLine(
      Instant date, String description, BigDecimal amount, BigDecimal balance) {}
}
