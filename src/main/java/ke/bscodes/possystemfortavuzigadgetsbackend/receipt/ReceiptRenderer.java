package ke.bscodes.possystemfortavuzigadgetsbackend.receipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lays out a sale as a receipt for 58mm (32 characters) or 80mm (48 characters)
 * paper. Prices include VAT (usual for Kenyan shelf prices); the VAT part is
 * shown separately. Shared module from BS Codes.
 */
public final class ReceiptRenderer {

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.of("Africa/Nairobi"));

  public record Line(String name, int quantity, BigDecimal unitPrice) {
    BigDecimal total() {
      return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
  }

  public record Receipt(
      String shopName,
      List<String> shopDetails,
      String receiptNumber,
      String cashier,
      Instant time,
      List<Line> lines,
      int vatRatePercent,
      String paymentMethod,
      String mpesaReceipt,
      List<String> footer) {}

  private ReceiptRenderer() {}

  public static BigDecimal total(Receipt receipt) {
    return receipt.lines().stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** VAT-inclusive prices: VAT = total × rate / (100 + rate). */
  public static BigDecimal vat(Receipt receipt) {
    if (receipt.vatRatePercent() <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal rate = BigDecimal.valueOf(receipt.vatRatePercent());
    return total(receipt).multiply(rate).divide(rate.add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
  }

  /** "Sugar", "480.00" → "Sugar         480.00" in `width` characters. */
  public static String columns(String left, String right, int width) {
    int room = width - right.length() - 1;
    String text = left.length() > room ? left.substring(0, room) : left;
    return text + " ".repeat(width - text.length() - right.length()) + right;
  }

  public static byte[] render(Receipt receipt, int width) {
    String rule = "-".repeat(width);
    EscPosBuilder p = new EscPosBuilder();
    p.align(EscPosBuilder.Align.CENTER).bold(true).large(true).line(receipt.shopName()).large(false).bold(false);
    receipt.shopDetails().forEach(p::line);
    p.align(EscPosBuilder.Align.LEFT).line(rule);
    p.line(columns("Receipt " + receipt.receiptNumber(), TIME.format(receipt.time()), width));
    p.line("Served by " + receipt.cashier()).line(rule);
    for (Line l : receipt.lines()) {
      p.line(l.name().length() > width ? l.name().substring(0, width) : l.name());
      p.line(columns("  " + l.quantity() + " x " + money(l.unitPrice()), money(l.total()), width));
    }
    p.line(rule).bold(true).line(columns("TOTAL KES", money(total(receipt)), width)).bold(false);
    if (receipt.vatRatePercent() > 0) {
      p.line(columns("Incl. VAT " + receipt.vatRatePercent() + "%", money(vat(receipt)), width));
    }
    p.line(columns("Paid by", receipt.paymentMethod(), width));
    if (receipt.mpesaReceipt() != null && !receipt.mpesaReceipt().isBlank()) {
      p.line(columns("M-Pesa ref", receipt.mpesaReceipt(), width));
    }
    p.line(rule).align(EscPosBuilder.Align.CENTER);
    receipt.footer().forEach(p::line);
    return p.feed(3).cut().build();
  }

  private static String money(BigDecimal amount) {
    return new DecimalFormat("#,##0.00").format(amount);
  }
}
