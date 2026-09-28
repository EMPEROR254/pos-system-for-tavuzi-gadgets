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
import ke.bscodes.possystemfortavuzigadgetsbackend.products.VatCategory;

/**
 * One line of a sale. Product name, unit, price, cost and VAT are copies taken at the time of sale,
 * so later price changes never alter an old receipt or report.
 */
@Entity
@Table(name = "sale_lines")
public class SaleLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id")
  private Sale sale;

  private int lineNo;
  private Long productId;
  private Long unitId;
  private String productName;
  private String unitName;
  private int unitFactor;
  private int quantity;
  private int returnedQuantity;
  private BigDecimal unitPrice;
  private BigDecimal discount;
  private BigDecimal lineTotal;

  @Enumerated(EnumType.STRING)
  private VatCategory vatCategory;

  private BigDecimal vatRate;
  private BigDecimal vatAmount;
  private BigDecimal unitCost;
  private int warrantyMonths;
  private Instant warrantyEndsAt;

  @OneToMany(mappedBy = "saleLine", cascade = CascadeType.ALL)
  @OrderBy("id")
  private List<SoldSerial> serials = new ArrayList<>();

  protected SaleLine() {}

  public SaleLine(Sale sale, int lineNo, SaleCalculator.PricedLine priced) {
    this.sale = sale;
    this.lineNo = lineNo;
    this.productId = priced.productId();
    this.unitId = priced.unitId();
    this.productName = priced.productName();
    this.unitName = priced.unitName();
    this.unitFactor = priced.unitFactor();
    this.quantity = priced.quantity();
    this.unitPrice = priced.unitPrice();
    this.discount = priced.discount();
    this.lineTotal = priced.lineTotal();
    this.vatCategory = priced.vatCategory();
    this.vatRate = priced.vatRate();
    this.vatAmount = priced.vatAmount();
    this.unitCost = priced.unitCost();
    this.warrantyMonths = priced.warrantyMonths();
    this.warrantyEndsAt = priced.warrantyEndsAt();
  }

  public void addSerial(String serial) {
    serials.add(new SoldSerial(this, serial));
  }

  public int returnableQuantity() {
    return quantity - returnedQuantity;
  }

  public void recordReturn(int qty) {
    returnedQuantity += qty;
  }

  public Long getId() {
    return id;
  }

  public Sale getSale() {
    return sale;
  }

  public int getLineNo() {
    return lineNo;
  }

  public Long getProductId() {
    return productId;
  }

  public String getProductName() {
    return productName;
  }

  public String getUnitName() {
    return unitName;
  }

  public int getUnitFactor() {
    return unitFactor;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getReturnedQuantity() {
    return returnedQuantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getDiscount() {
    return discount;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public VatCategory getVatCategory() {
    return vatCategory;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
  }

  public Instant getWarrantyEndsAt() {
    return warrantyEndsAt;
  }

  public List<SoldSerial> getSerials() {
    return serials;
  }
}
