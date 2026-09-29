package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
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

/** Goods received from a supplier (a delivery / supplier invoice). */
@Entity
@Table(name = "purchases")
public class Purchase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "supplier_id")
  private Supplier supplier;

  private String invoiceNo;
  private BigDecimal total = BigDecimal.ZERO.setScale(2);
  private Long receivedBy;
  private Instant receivedAt;
  private String note;

  @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
  @OrderBy("id")
  private List<PurchaseLine> lines = new ArrayList<>();

  protected Purchase() {}

  public Purchase(Supplier supplier, String invoiceNo, String note, long receivedBy, Instant now) {
    this.supplier = supplier;
    this.invoiceNo = invoiceNo;
    this.note = note;
    this.receivedBy = receivedBy;
    this.receivedAt = now;
  }

  public void addLine(PurchaseLine line) {
    lines.add(line);
    total = total.add(line.getLineTotal());
  }

  public Long getId() {
    return id;
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public String getInvoiceNo() {
    return invoiceNo;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getNote() {
    return note;
  }

  public List<PurchaseLine> getLines() {
    return lines;
  }
}
