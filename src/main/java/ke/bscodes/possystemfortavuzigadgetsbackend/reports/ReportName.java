package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.util.Arrays;
import java.util.Optional;

/** The reports the shop asked for, by their URL name (/api/reports/daily-sales). */
public enum ReportName {
  DAILY_SALES("daily-sales", "Daily sales summary"),
  PRODUCT_PROFIT("product-profit", "Profit per product"),
  CASHIERS("cashiers", "Sales per cashier"),
  SHIFTS("shifts", "Shift cash-ups"),
  STOCK_VALUE("stock-value", "Stock value"),
  BEST_SELLERS("best-sellers", "Best sellers"),
  SLOW_SELLERS("slow-sellers", "Slow sellers"),
  MPESA("mpesa", "M-Pesa reconciliation"),
  VAT("vat", "VAT report");

  private final String slug;
  private final String title;

  ReportName(String slug, String title) {
    this.slug = slug;
    this.title = title;
  }

  public String slug() {
    return slug;
  }

  public String title() {
    return title;
  }

  public static Optional<ReportName> fromSlug(String slug) {
    return Arrays.stream(values()).filter(r -> r.slug.equals(slug)).findFirst();
  }
}
