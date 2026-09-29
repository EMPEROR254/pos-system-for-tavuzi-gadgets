package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductCostTest {

  @Test
  void costIsTheWeightedAverageOfShelfAndDelivery() {
    Product p = new Product(Instant.EPOCH);
    p.update(
        "Cable",
        null,
        null,
        VatCategory.EXEMPT,
        SerialType.NONE,
        0,
        new BigDecimal("100.00"),
        true,
        Instant.EPOCH);
    p.changeStock(10);
    // 10 on the shelf at 100 + 30 received at 140 = 40 at 130.
    p.receiveAtCost(30, new BigDecimal("140"));
    assertThat(p.getCostPrice()).isEqualByComparingTo("130.00");
  }

  @Test
  void lowStockNeedsAReorderLevel() {
    Product p = new Product(Instant.EPOCH);
    assertThat(p.isLowOnStock()).isFalse();
    p.setReorderLevel(2);
    p.changeStock(2);
    assertThat(p.isLowOnStock()).isTrue();
  }
}
