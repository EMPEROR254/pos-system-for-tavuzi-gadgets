package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Money paid to a supplier (reduces what the shop owes them). */
@Entity
@Table(name = "supplier_payments")
public class SupplierPayment {

  public enum Method {
    CASH,
    MPESA,
    BANK,
    CHEQUE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long supplierId;
  private Long purchaseId;
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  private Method method;

  private String reference;
  private Long paidBy;
  private Instant paidAt;

  protected SupplierPayment() {}

  public BigDecimal getAmount() {
    return amount;
  }

  public Method getMethod() {
    return method;
  }

  public String getReference() {
    return reference;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public SupplierPayment(
      long supplierId,
      Long purchaseId,
      BigDecimal amount,
      Method method,
      String reference,
      long paidBy,
      Instant now) {
    this.supplierId = supplierId;
    this.purchaseId = purchaseId;
    this.amount = amount;
    this.method = method;
    this.reference = reference;
    this.paidBy = paidBy;
    this.paidAt = now;
  }
}
