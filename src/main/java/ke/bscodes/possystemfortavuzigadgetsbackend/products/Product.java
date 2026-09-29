package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Something the shop sells. It is sold in one or more units (e.g. "Piece" and "Carton of 20"), each
 * with its own retail and wholesale price. Products are never deleted, only deactivated, because
 * old sales point at them.
 */
@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String sku;
  private String category;

  @Enumerated(EnumType.STRING)
  private VatCategory vatCategory;

  @Enumerated(EnumType.STRING)
  private SerialType serialType;

  private int warrantyMonths;
  private BigDecimal costPrice;
  private boolean active = true;

  /** In base units (e.g. pieces). Only StockService changes it, always with a movement. */
  private int stockOnHand;

  /** Low-stock alert when stock is at or below this (0 = no alert). */
  private int reorderLevel;

  private Instant createdAt;
  private Instant updatedAt;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder, id")
  private List<ProductUnit> units = new ArrayList<>();

  protected Product() {}

  public Product(Instant now) {
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(
      String name,
      String sku,
      String category,
      VatCategory vatCategory,
      SerialType serialType,
      int warrantyMonths,
      BigDecimal costPrice,
      boolean active,
      Instant now) {
    this.name = name;
    this.sku = sku;
    this.category = category;
    this.vatCategory = vatCategory;
    this.serialType = serialType;
    this.warrantyMonths = warrantyMonths;
    this.costPrice = costPrice;
    this.active = active;
    this.updatedAt = now;
  }

  public void setReorderLevel(int reorderLevel) {
    this.reorderLevel = reorderLevel;
  }

  /** Adds (or with a negative delta removes) base units; returns the new balance. */
  public int changeStock(int delta) {
    stockOnHand += delta;
    return stockOnHand;
  }

  /**
   * Stock received at a cost per base unit: the cost price becomes the weighted average of what is
   * on the shelf and what came in, so profit reports stay fair when supplier prices change.
   */
  public void receiveAtCost(int baseUnits, BigDecimal costPerBaseUnit) {
    int onShelf = Math.max(stockOnHand, 0);
    if (onShelf == 0 || costPrice == null) {
      costPrice = costPerBaseUnit.setScale(2, RoundingMode.HALF_UP);
    } else {
      costPrice =
          costPrice
              .multiply(BigDecimal.valueOf(onShelf))
              .add(costPerBaseUnit.multiply(BigDecimal.valueOf(baseUnits)))
              .divide(BigDecimal.valueOf(onShelf + baseUnits), 2, RoundingMode.HALF_UP);
    }
  }

  public boolean isLowOnStock() {
    return reorderLevel > 0 && stockOnHand <= reorderLevel;
  }

  public Optional<ProductUnit> unit(long unitId) {
    return units.stream().filter(u -> u.getId() != null && u.getId() == unitId).findFirst();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSku() {
    return sku;
  }

  public String getCategory() {
    return category;
  }

  public VatCategory getVatCategory() {
    return vatCategory;
  }

  public SerialType getSerialType() {
    return serialType;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
  }

  public BigDecimal getCostPrice() {
    return costPrice;
  }

  public boolean isActive() {
    return active;
  }

  public int getStockOnHand() {
    return stockOnHand;
  }

  public int getReorderLevel() {
    return reorderLevel;
  }

  public List<ProductUnit> getUnits() {
    return units;
  }
}
