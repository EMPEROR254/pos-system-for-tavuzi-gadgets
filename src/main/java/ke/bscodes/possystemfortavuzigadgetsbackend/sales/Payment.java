package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One payment towards a sale; a sale can have several (e.g. part cash, part M-Pesa). */
@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id")
  private Sale sale;

  @Enumerated(EnumType.STRING)
  private PaymentMethod method;

  private BigDecimal amount;

  /** M-Pesa code (e.g. SJK4H7XYZ1) or the card machine's approval code. */
  private String reference;

  private Instant createdAt;

  protected Payment() {}

  public Payment(
      Sale sale, PaymentMethod method, BigDecimal amount, String reference, Instant now) {
    this.sale = sale;
    this.method = method;
    this.amount = amount;
    this.reference = reference;
    this.createdAt = now;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getReference() {
    return reference;
  }
}
