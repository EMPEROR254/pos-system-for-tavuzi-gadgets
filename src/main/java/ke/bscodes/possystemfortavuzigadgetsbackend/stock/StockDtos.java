package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;

/** Request and response bodies for /api/stock. */
public final class StockDtos {

  private StockDtos() {}

  /** One product's stock (in base units, e.g. pieces) and its value at cost. */
  public record StockRow(
      long productId,
      String name,
      String sku,
      String category,
      String serialType,
      String baseUnit,
      int stockOnHand,
      int reorderLevel,
      boolean low,
      BigDecimal costPrice,
      BigDecimal stockValue) {

    static StockRow from(Product p) {
      String base =
          p.getUnits().stream()
              .filter(u -> u.getFactor() == 1)
              .map(u -> u.getName())
              .findFirst()
              .orElse("Piece");
      return new StockRow(
          p.getId(),
          p.getName(),
          p.getSku(),
          p.getCategory(),
          p.getSerialType().name(),
          base,
          p.getStockOnHand(),
          p.getReorderLevel(),
          p.isLowOnStock(),
          p.getCostPrice(),
          p.getCostPrice().multiply(BigDecimal.valueOf(Math.max(p.getStockOnHand(), 0))));
    }
  }

  public record StockSummary(long lowStockCount, BigDecimal stockValue, Instant lastCountAt) {}

  public record MovementView(
      long id,
      String type,
      int quantity,
      int balanceAfter,
      String reason,
      String note,
      String reference,
      String userName,
      Instant createdAt) {}

  /**
   * A change by hand, in base units: positive adds, negative removes. For IMEI/serial products,
   * list exactly that many IMEIs/serials (new ones when adding, ones on the shelf when removing).
   */
  public record AdjustRequest(
      @NotNull(message = "Choose a product.") Long productId,
      int quantity,
      @NotNull(message = "Choose a reason.") AdjustmentReason reason,
      @Size(max = 200, message = "At most 200 characters.") String note,
      @Size(max = 500, message = "Too many serial numbers.") List<String> serials) {}
}
