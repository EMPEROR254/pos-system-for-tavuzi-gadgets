package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductUnit;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.VatCategory;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.Vat;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.Serials;

/**
 * Prices a cart on the server: the till only says what and how many; prices always come from the
 * catalogue, so a tampered or out-of-date till can't change them. No database access - easy to
 * test.
 */
public final class SaleCalculator {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

  public record PricedLine(
      long productId,
      long unitId,
      String productName,
      String unitName,
      int unitFactor,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal discount,
      BigDecimal lineTotal,
      VatCategory vatCategory,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal unitCost,
      int warrantyMonths,
      Instant warrantyEndsAt,
      List<String> serials) {

    BigDecimal gross() {
      return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
  }

  public record Totals(
      BigDecimal subtotal, BigDecimal discountTotal, BigDecimal vatTotal, BigDecimal total) {}

  public record PricedCart(List<PricedLine> lines, Totals totals) {
    public boolean hasDiscount() {
      return totals.discountTotal().signum() > 0;
    }
  }

  private SaleCalculator() {}

  public static PricedCart price(
      List<LineRequest> requested,
      Map<Long, Product> products,
      PriceTier tier,
      Vat vat,
      Instant now) {
    List<PricedLine> lines = new ArrayList<>();
    Set<String> serialsInCart = new HashSet<>();
    for (LineRequest r : requested) {
      Product product = products.get(r.productId());
      if (product == null || !product.isActive()) {
        throw ApiException.badRequest("A product in the cart is no longer sold. Remove it.");
      }
      ProductUnit unit =
          product
              .unit(r.unitId())
              .orElseThrow(
                  () ->
                      ApiException.badRequest(
                          product.getName() + ": that unit was changed. Add the item again."));
      BigDecimal unitPrice = unit.priceFor(tier == PriceTier.WHOLESALE);
      BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(r.quantity()));
      BigDecimal discount =
          r.discount() == null ? zero() : r.discount().setScale(2, RoundingMode.HALF_UP);
      if (discount.compareTo(gross) > 0) {
        throw ApiException.badRequest(
            product.getName() + ": the discount is bigger than the line price.");
      }
      List<String> serials = checkSerials(product, unit, r, serialsInCart);
      BigDecimal lineTotal = gross.subtract(discount);
      BigDecimal rate = vat.ratePercent(product.getVatCategory());
      lines.add(
          new PricedLine(
              product.getId(),
              unit.getId(),
              product.getName(),
              unit.getName(),
              unit.getFactor(),
              r.quantity(),
              unitPrice,
              discount,
              lineTotal,
              product.getVatCategory(),
              rate,
              Vat.included(lineTotal, rate),
              product.getCostPrice().multiply(BigDecimal.valueOf(unit.getFactor())),
              product.getWarrantyMonths(),
              product.getWarrantyMonths() > 0
                  ? now.atZone(NAIROBI).plusMonths(product.getWarrantyMonths()).toInstant()
                  : null,
              serials));
    }
    return new PricedCart(lines, totals(lines));
  }

  private static List<String> checkSerials(
      Product product, ProductUnit unit, LineRequest r, Set<String> serialsInCart) {
    if (product.getSerialType() == SerialType.NONE) {
      return List.of();
    }
    int needed = r.quantity() * unit.getFactor();
    List<String> given =
        r.serials() == null
            ? List.of()
            : r.serials().stream().map(Serials::normalise).filter(s -> !s.isEmpty()).toList();
    String kind = product.getSerialType() == SerialType.IMEI ? "IMEI" : "serial number";
    if (given.size() != needed) {
      throw ApiException.badRequest(
          product.getName() + ": enter " + needed + " " + kind + (needed == 1 ? "" : "s") + ".");
    }
    for (String serial : given) {
      String problem = Serials.problem(product.getSerialType(), serial);
      if (problem != null) {
        throw ApiException.badRequest(product.getName() + ": " + problem);
      }
      if (!serialsInCart.add(serial)) {
        throw ApiException.badRequest(serial + " is entered twice.");
      }
    }
    return given;
  }

  static Totals totals(List<PricedLine> lines) {
    BigDecimal subtotal = zero();
    BigDecimal discount = zero();
    BigDecimal vat = zero();
    BigDecimal total = zero();
    for (PricedLine l : lines) {
      subtotal = subtotal.add(l.gross());
      discount = discount.add(l.discount());
      vat = vat.add(l.vatAmount());
      total = total.add(l.lineTotal());
    }
    return new Totals(subtotal, discount, vat, total);
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
