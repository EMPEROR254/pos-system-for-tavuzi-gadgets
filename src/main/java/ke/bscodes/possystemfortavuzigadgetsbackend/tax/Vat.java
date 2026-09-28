package ke.bscodes.possystemfortavuzigadgetsbackend.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.VatCategory;
import org.springframework.stereotype.Component;

/**
 * VAT for VAT-inclusive shelf prices (normal in Kenyan retail): the VAT inside an amount is amount
 * × rate / (100 + rate), rounded to the cent. Worked out per sale line, so a sale mixing
 * standard-rated phones and exempt items gets the right total.
 */
@Component
public class Vat {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final TaxProperties properties;

  public Vat(TaxProperties properties) {
    this.properties = properties;
  }

  /** The rate in percent for a category: the configured rate for STANDARD, otherwise 0. */
  public BigDecimal ratePercent(VatCategory category) {
    return category == VatCategory.STANDARD
        ? properties.vatRatePercent().setScale(2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO.setScale(2);
  }

  /** The VAT part of a VAT-inclusive amount. */
  public static BigDecimal included(BigDecimal grossAmount, BigDecimal ratePercent) {
    if (ratePercent.signum() == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return grossAmount
        .multiply(ratePercent)
        .divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
  }
}
