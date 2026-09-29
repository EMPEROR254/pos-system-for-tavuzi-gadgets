package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import java.math.BigDecimal;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;

/** Everything a printed or on-screen receipt shows (etims is null before eTIMS existed). */
public record ReceiptView(
    String shopName,
    List<String> shopDetails,
    List<String> paymentInstructions,
    SaleView sale,
    List<VatLine> vatBreakdown,
    String returnPolicy,
    List<String> footer,
    EtimsService.ReceiptInfo etims) {

  /** VAT per tax code, as KRA expects: taxable amount (without VAT), VAT, and total. */
  public record VatLine(
      String code, BigDecimal ratePercent, BigDecimal taxable, BigDecimal vat, BigDecimal gross) {}
}
