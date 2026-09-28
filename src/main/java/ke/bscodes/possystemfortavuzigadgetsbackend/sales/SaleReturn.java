package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Items brought back from a sale, approved by the owner, and the money given back. */
@Entity
@Table(name = "sale_returns")
public class SaleReturn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id")
  private Sale sale;

  private Long processedBy;
  private Long approvedBy;
  private String reason;

  @Enumerated(EnumType.STRING)
  private PaymentMethod refundMethod;

  private BigDecimal refundAmount = BigDecimal.ZERO.setScale(2);
  private BigDecimal vatAmount = BigDecimal.ZERO.setScale(2);
  private Instant createdAt;

  @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL)
  private List<SaleReturnLine> lines = new ArrayList<>();

  protected SaleReturn() {}

  public SaleReturn(
      Sale sale,
      long processedBy,
      long approvedBy,
      String reason,
      PaymentMethod refundMethod,
      Instant now) {
    this.sale = sale;
    this.processedBy = processedBy;
    this.approvedBy = approvedBy;
    this.reason = reason;
    this.refundMethod = refundMethod;
    this.createdAt = now;
  }

  public void addLine(SaleLine saleLine, int quantity, BigDecimal amount, BigDecimal vat) {
    lines.add(new SaleReturnLine(this, saleLine, quantity, amount, vat));
    refundAmount = refundAmount.add(amount);
    vatAmount = vatAmount.add(vat);
  }

  public Long getId() {
    return id;
  }

  public BigDecimal getRefundAmount() {
    return refundAmount;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }
}
