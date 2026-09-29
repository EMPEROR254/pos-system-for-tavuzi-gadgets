package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One change to a product's stock, with the balance after it (never edited or deleted). */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long productId;

  @Enumerated(EnumType.STRING)
  private MovementType type;

  private int quantity;
  private int balanceAfter;
  private String reason;
  private String note;
  private String reference;
  private Long userId;
  private Instant createdAt;

  protected StockMovement() {}

  public StockMovement(
      long productId,
      MovementType type,
      int quantity,
      int balanceAfter,
      String reason,
      String note,
      String reference,
      long userId,
      Instant now) {
    this.productId = productId;
    this.type = type;
    this.quantity = quantity;
    this.balanceAfter = balanceAfter;
    this.reason = reason;
    this.note = note;
    this.reference = reference;
    this.userId = userId;
    this.createdAt = now;
  }
}
