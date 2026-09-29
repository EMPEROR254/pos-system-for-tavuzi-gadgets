package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.ApprovalService.Approval;

/** Request and response bodies for /api/sales. */
public final class SaleDtos {

  private SaleDtos() {}

  public record LineRequest(
      @NotNull(message = "Choose a product.") Long productId,
      @NotNull(message = "Choose a unit.") Long unitId,
      @Min(value = 1, message = "At least 1.") @Max(value = 10000, message = "At most 10,000.")
          int quantity,
      @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal discount,
      @Size(max = 500, message = "Too many serial numbers.") List<String> serials) {}

  public record PaymentRequest(
      @NotNull(message = "Choose how the customer paid.") PaymentMethod method,
      @NotNull(message = "Enter the amount.")
          @DecimalMin(value = "0.01", message = "Must be more than 0.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal amount,
      @Size(max = 40, message = "At most 40 characters.") String reference) {}

  /**
   * A finished cart from the till. The customer is customerId, or found/added by phone
   * (marketingOptIn = they agreed to promotions); smsReceipt queues an SMS receipt. clientRef is a
   * random id made by the till: sending the same sale twice (double tap, retry after a network
   * error) returns the first sale, not a second one.
   */
  public record CheckoutRequest(
      @NotBlank(message = "Missing sale id.") @Size(max = 64) String clientRef,
      @NotNull(message = "Choose retail or wholesale.") PriceTier priceTier,
      @NotEmpty(message = "The cart is empty.") @Size(max = 200) @Valid List<LineRequest> lines,
      @NotNull(message = "Add a payment.") @Valid List<PaymentRequest> payments,
      @Size(max = 100, message = "At most 100 characters.") String customerName,
      @Size(max = 20, message = "Too long for a phone number.") String customerPhone,
      Approval approval,
      Long customerId,
      boolean marketingOptIn,
      boolean smsReceipt) {}

  public record SerialView(String serial, boolean returned) {}

  public record LineView(
      long id,
      String productName,
      String unitName,
      int quantity,
      int returnedQuantity,
      BigDecimal unitPrice,
      BigDecimal discount,
      BigDecimal lineTotal,
      String vatCode,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      Instant warrantyEndsAt,
      List<SerialView> serials) {}

  public record PaymentView(String method, String label, BigDecimal amount, String reference) {}

  public record SaleView(
      long id,
      String receiptNo,
      Instant createdAt,
      String cashierName,
      String status,
      String priceTier,
      String customerName,
      String customerPhone,
      BigDecimal subtotal,
      BigDecimal discountTotal,
      BigDecimal vatTotal,
      BigDecimal total,
      BigDecimal paidTotal,
      BigDecimal changeDue,
      List<LineView> lines,
      List<PaymentView> payments) {}

  /** A row in the sales list (no lines - one query, no N+1). */
  public record SaleSummary(
      long id,
      String receiptNo,
      Instant createdAt,
      String cashierName,
      String status,
      BigDecimal total,
      String customerName) {}

  public record ReturnLineRequest(
      @NotNull Long saleLineId,
      @Min(value = 1, message = "At least 1.") int quantity,
      List<String> serials) {}

  public record ReturnRequest(
      @NotEmpty(message = "Choose what is being returned.") @Valid List<ReturnLineRequest> lines,
      @NotBlank(message = "Write why it is being returned.")
          @Size(max = 300, message = "At most 300 characters.")
          String reason,
      @NotNull(message = "Choose how the money is given back.") PaymentMethod refundMethod,
      Approval approval) {}

  public record ReturnView(
      long id, String receiptNo, BigDecimal refundAmount, BigDecimal vatAmount, String status) {}
}
