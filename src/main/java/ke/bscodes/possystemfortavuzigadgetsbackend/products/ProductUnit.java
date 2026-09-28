package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One way of selling a product: its name, how many base units it holds (factor) and its prices (VAT
 * included). A missing wholesale price means the retail price is used for wholesale too.
 */
@Entity
@Table(name = "product_units")
public class ProductUnit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  private String name;
  private int factor;
  private String barcode;
  private BigDecimal retailPrice;
  private BigDecimal wholesalePrice;
  private int sortOrder;

  protected ProductUnit() {}

  public ProductUnit(Product product) {
    this.product = product;
  }

  public void update(
      String name,
      int factor,
      String barcode,
      BigDecimal retailPrice,
      BigDecimal wholesalePrice,
      int sortOrder) {
    this.name = name;
    this.factor = factor;
    this.barcode = barcode;
    this.retailPrice = retailPrice;
    this.wholesalePrice = wholesalePrice;
    this.sortOrder = sortOrder;
  }

  /** The price for a price tier; wholesale falls back to retail when not set. */
  public BigDecimal priceFor(boolean wholesale) {
    return wholesale && wholesalePrice != null ? wholesalePrice : retailPrice;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getFactor() {
    return factor;
  }

  public String getBarcode() {
    return barcode;
  }

  public BigDecimal getRetailPrice() {
    return retailPrice;
  }

  public BigDecimal getWholesalePrice() {
    return wholesalePrice;
  }
}
