package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

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

/** One product in a count: what the system expected and what was counted (null = not yet). */
@Entity
@Table(name = "stock_count_lines")
public class StockCountLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "count_id")
  private StockCount count;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  private int expected;
  private Integer counted;
  private BigDecimal unitCost;

  protected StockCountLine() {}

  public StockCountLine(StockCount count, Product product) {
    this.count = count;
    this.product = product;
    this.expected = product.getStockOnHand();
    this.unitCost = product.getCostPrice();
  }

  public void setCounted(Integer counted) {
    this.counted = counted;
  }

  public Integer difference() {
    return counted == null ? null : counted - expected;
  }

  public Product getProduct() {
    return product;
  }

  public int getExpected() {
    return expected;
  }

  public Integer getCounted() {
    return counted;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }
}
