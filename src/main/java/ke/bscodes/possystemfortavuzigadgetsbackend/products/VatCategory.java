package ke.bscodes.possystemfortavuzigadgetsbackend.products;

/**
 * How VAT applies to a product. The standard rate (16%) comes from configuration (app.tax), so a
 * rate change needs no code change. The letters are the KRA eTIMS tax codes shown on receipts.
 */
public enum VatCategory {
  STANDARD("B"),
  ZERO_RATED("C"),
  EXEMPT("A");

  private final String etimsCode;

  VatCategory(String etimsCode) {
    this.etimsCode = etimsCode;
  }

  public String etimsCode() {
    return etimsCode;
  }
}
