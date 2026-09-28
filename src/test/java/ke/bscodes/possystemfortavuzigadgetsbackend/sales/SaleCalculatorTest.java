package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductUnit;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.VatCategory;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.TaxProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.Vat;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.SerialsTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SaleCalculatorTest {

  private final Instant now = Instant.parse("2026-09-28T07:00:00Z");
  private final Vat vat = new Vat(new TaxProperties(new BigDecimal("16")));

  /**
   * A phone (16% VAT, IMEI, 12-month warranty) and exempt cables sold per piece or carton of 20.
   */
  private final Product phone =
      product(1, "Tecno Spark", VatCategory.STANDARD, SerialType.IMEI, 12, "11600", null, 1);

  private final Product cable =
      product(2, "USB cable", VatCategory.EXEMPT, SerialType.NONE, 0, "200", "150", 1);

  {
    ProductUnit carton = new ProductUnit(cable);
    carton.update("Carton of 20", 20, null, new BigDecimal("3600"), new BigDecimal("3000"), 1);
    ReflectionTestUtils.setField(carton, "id", 22L);
    cable.getUnits().add(carton);
  }

  private final Map<Long, Product> catalogue = Map.of(1L, phone, 2L, cable);

  @Test
  void pricesFromTheCatalogueWithVatPerLine() {
    String imei = SerialsTest.imei("35693803564380");
    var cart =
        SaleCalculator.price(
            List.of(
                new LineRequest(1L, 11L, 1, null, List.of(imei)),
                new LineRequest(2L, 21L, 2, null, null)),
            catalogue,
            PriceTier.RETAIL,
            vat,
            now);

    var phoneLine = cart.lines().get(0);
    assertThat(phoneLine.lineTotal()).isEqualByComparingTo("11600");
    // 11600 × 16 / 116 = 1600 VAT inside the price.
    assertThat(phoneLine.vatAmount()).isEqualByComparingTo("1600.00");
    assertThat(phoneLine.warrantyEndsAt()).isEqualTo(Instant.parse("2027-09-28T07:00:00Z"));
    assertThat(cart.lines().get(1).vatAmount()).isEqualByComparingTo("0");
    assertThat(cart.totals().total()).isEqualByComparingTo("12000");
    assertThat(cart.totals().vatTotal()).isEqualByComparingTo("1600");
  }

  @Test
  void wholesaleUsesWholesalePricesAndCartonUnits() {
    var cart =
        SaleCalculator.price(
            List.of(new LineRequest(2L, 22L, 2, null, null)),
            catalogue,
            PriceTier.WHOLESALE,
            vat,
            now);
    assertThat(cart.lines().getFirst().unitPrice()).isEqualByComparingTo("3000");
    assertThat(cart.totals().total()).isEqualByComparingTo("6000");
  }

  @Test
  void discountReducesTheLineAndItsVat() {
    String imei = SerialsTest.imei("35693803564380");
    var cart =
        SaleCalculator.price(
            List.of(new LineRequest(1L, 11L, 1, new BigDecimal("600"), List.of(imei))),
            catalogue,
            PriceTier.RETAIL,
            vat,
            now);
    assertThat(cart.hasDiscount()).isTrue();
    assertThat(cart.totals().total()).isEqualByComparingTo("11000");
    assertThat(cart.totals().vatTotal()).isEqualByComparingTo("1517.24");
    assertThatThrownBy(
            () ->
                SaleCalculator.price(
                    List.of(new LineRequest(2L, 21L, 1, new BigDecimal("201"), null)),
                    catalogue,
                    PriceTier.RETAIL,
                    vat,
                    now))
        .hasMessageContaining("discount is bigger");
  }

  @Test
  void phonesNeedOneValidImeiEach() {
    assertThatThrownBy(
            () ->
                SaleCalculator.price(
                    List.of(
                        new LineRequest(
                            1L, 11L, 2, null, List.of(SerialsTest.imei("35693803564380")))),
                    catalogue,
                    PriceTier.RETAIL,
                    vat,
                    now))
        .hasMessageContaining("enter 2 IMEIs");
    String same = SerialsTest.imei("35693803564380");
    assertThatThrownBy(
            () ->
                SaleCalculator.price(
                    List.of(new LineRequest(1L, 11L, 2, null, List.of(same, same))),
                    catalogue,
                    PriceTier.RETAIL,
                    vat,
                    now))
        .hasMessageContaining("entered twice");
  }

  @Test
  void refusesUnknownUnitsAndInactiveProducts() {
    assertThatThrownBy(
            () ->
                SaleCalculator.price(
                    List.of(new LineRequest(2L, 99L, 1, null, null)),
                    catalogue,
                    PriceTier.RETAIL,
                    vat,
                    now))
        .hasMessageContaining("unit was changed");
    assertThatThrownBy(
            () ->
                SaleCalculator.price(
                    List.of(new LineRequest(7L, 1L, 1, null, null)),
                    catalogue,
                    PriceTier.RETAIL,
                    vat,
                    now))
        .hasMessageContaining("no longer sold");
  }

  private Product product(
      long id,
      String name,
      VatCategory vatCategory,
      SerialType serialType,
      int warrantyMonths,
      String retail,
      String wholesale,
      int factor) {
    Product p = new Product(now);
    p.update(
        name,
        null,
        null,
        vatCategory,
        serialType,
        warrantyMonths,
        new BigDecimal("100"),
        true,
        now);
    ReflectionTestUtils.setField(p, "id", id);
    ProductUnit unit = new ProductUnit(p);
    unit.update(
        "Piece",
        factor,
        null,
        new BigDecimal(retail),
        wholesale == null ? null : new BigDecimal(wholesale),
        0);
    ReflectionTestUtils.setField(unit, "id", id * 10 + 1);
    p.getUnits().add(unit);
    return p;
  }
}
