package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;

/** One product on a delivery: how many of which unit, at what cost per unit. */
@Entity
@Table(name = "purchase_lines")
public class PurchaseLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_id")
  private Purchase purchase;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  private String unitName;
  private int unitFactor;
  private int quantity;
  private BigDecimal unitCost;
  private BigDecimal lineTotal;

  protected PurchaseLine() {}

  public PurchaseLine(
      Purchase purchase,
      Product product,
      String unitName,
      int unitFactor,
      int quantity,
      BigDecimal unitCost) {
    this.purchase = purchase;
    this.product = product;
    this.unitName = unitName;
    this.unitFactor = unitFactor;
    this.quantity = quantity;
    this.unitCost = unitCost;
    this.lineTotal = unitCost.multiply(BigDecimal.valueOf(quantity));
  }

  public Product getProduct() {
    return product;
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

  public BigDecimal getUnitCost() {
    return unitCost;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }
}
