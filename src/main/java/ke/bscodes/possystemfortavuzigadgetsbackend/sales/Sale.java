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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.User;

/** A completed sale: its lines, payments and totals (all amounts VAT-inclusive, KES). */
@Entity
@Table(name = "sales")
public class Sale {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String receiptNo;
  private String clientRef;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cashier_id")
  private User cashier;

  private Long discountApprovedBy;

  @Enumerated(EnumType.STRING)
  private PriceTier priceTier;

  @Enumerated(EnumType.STRING)
  private SaleStatus status = SaleStatus.COMPLETED;

  private String customerName;
  private String customerPhone;
  private BigDecimal subtotal;
  private BigDecimal discountTotal;
  private BigDecimal vatTotal;
  private BigDecimal total;
  private BigDecimal paidTotal;
  private BigDecimal changeDue;
  private Instant createdAt;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
  @OrderBy("lineNo")
  private List<SaleLine> lines = new ArrayList<>();

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
  @OrderBy("id")
  private List<Payment> payments = new ArrayList<>();

  protected Sale() {}

  public Sale(
      String receiptNo,
      String clientRef,
      User cashier,
      PriceTier priceTier,
      String customerName,
      String customerPhone,
      Instant now) {
    this.receiptNo = receiptNo;
    this.clientRef = clientRef;
    this.cashier = cashier;
    this.priceTier = priceTier;
    this.customerName = customerName;
    this.customerPhone = customerPhone;
    this.createdAt = now;
  }

  public void addLine(SaleLine line) {
    lines.add(line);
  }

  public void addPayment(Payment payment) {
    payments.add(payment);
  }

  /** Totals are worked out by SaleCalculator; this only stores them. */
  public void setTotals(
      BigDecimal subtotal,
      BigDecimal discountTotal,
      BigDecimal vatTotal,
      BigDecimal total,
      BigDecimal paidTotal,
      BigDecimal changeDue,
      Long discountApprovedBy) {
    this.subtotal = subtotal;
    this.discountTotal = discountTotal;
    this.vatTotal = vatTotal;
    this.total = total;
    this.paidTotal = paidTotal;
    this.changeDue = changeDue;
    this.discountApprovedBy = discountApprovedBy;
  }

  /** After a return: RETURNED once every item is back, otherwise PARTLY_RETURNED. */
  public void refreshReturnStatus() {
    boolean all = lines.stream().allMatch(l -> l.getReturnedQuantity() >= l.getQuantity());
    boolean any = lines.stream().anyMatch(l -> l.getReturnedQuantity() > 0);
    status = all ? SaleStatus.RETURNED : any ? SaleStatus.PARTLY_RETURNED : SaleStatus.COMPLETED;
  }

  public Long getId() {
    return id;
  }

  public String getReceiptNo() {
    return receiptNo;
  }

  public String getClientRef() {
    return clientRef;
  }

  public User getCashier() {
    return cashier;
  }

  public Long getDiscountApprovedBy() {
    return discountApprovedBy;
  }

  public PriceTier getPriceTier() {
    return priceTier;
  }

  public SaleStatus getStatus() {
    return status;
  }

  public String getCustomerName() {
    return customerName;
  }

  public String getCustomerPhone() {
    return customerPhone;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getDiscountTotal() {
    return discountTotal;
  }

  public BigDecimal getVatTotal() {
    return vatTotal;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public BigDecimal getPaidTotal() {
    return paidTotal;
  }

  public BigDecimal getChangeDue() {
    return changeDue;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<SaleLine> getLines() {
    return lines;
  }

  public List<Payment> getPayments() {
    return payments;
  }
}
