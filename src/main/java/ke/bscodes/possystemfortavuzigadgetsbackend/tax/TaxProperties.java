package ke.bscodes.possystemfortavuzigadgetsbackend.tax;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAT settings from application.yml under "app.tax". The standard rate lives here, not in code, so
 * a change by KRA only needs a configuration change (VAT_RATE_PERCENT).
 */
@ConfigurationProperties(prefix = "app.tax")
public record TaxProperties(BigDecimal vatRatePercent) {

  public TaxProperties {
    if (vatRatePercent == null) {
      vatRatePercent = new BigDecimal("16");
    }
  }
}
