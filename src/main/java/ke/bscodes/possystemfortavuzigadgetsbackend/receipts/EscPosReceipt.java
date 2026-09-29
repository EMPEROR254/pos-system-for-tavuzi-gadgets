package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipt.EscPosBuilder;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipt.EscPosBuilder.Align;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipt.ReceiptRenderer;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ReceiptView.VatLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SerialView;

/**
 * Lays out a till receipt for a thermal printer with the shared ESC/POS builder. The shared
 * ReceiptRenderer handles one VAT rate and one payment; this shop needs split payments, discounts,
 * VAT-exempt items and IMEIs with warranty dates, so the layout lives here.
 */
public final class EscPosReceipt {

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Nairobi"));
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Africa/Nairobi"));

  private EscPosReceipt() {}

  public static byte[] render(ReceiptView r, int width) {
    SaleView s = r.sale();
    String rule = "-".repeat(width);
    EscPosBuilder p = new EscPosBuilder();
    p.align(Align.CENTER).bold(true).large(true).line(r.shopName()).large(false).bold(false);
    r.shopDetails().forEach(p::line);
    p.align(Align.LEFT).line(rule);
    p.line(col("Receipt " + s.receiptNo(), TIME.format(s.createdAt()), width));
    p.line("Served by " + s.cashierName());
    if (s.customerName() != null) {
      p.line("Customer: " + s.customerName());
    }
    if ("WHOLESALE".equals(s.priceTier())) {
      p.line("Wholesale prices");
    }
    p.line(rule);
    for (LineView l : s.lines()) {
      p.line(fit(l.productName() + " [" + l.vatCode() + "]", width));
      p.line(
          col(
              "  " + l.quantity() + " " + l.unitName() + " x " + money(l.unitPrice()),
              money(l.lineTotal().add(l.discount())),
              width));
      if (l.discount().signum() > 0) {
        p.line(col("  Discount", "-" + money(l.discount()), width));
      }
      for (SerialView serial : l.serials()) {
        p.line(fit("  IMEI/SN: " + serial.serial(), width));
      }
      if (l.warrantyEndsAt() != null) {
        p.line("  Warranty until " + DATE.format(l.warrantyEndsAt()));
      }
    }
    p.line(rule);
    if (s.discountTotal().signum() > 0) {
      p.line(col("Subtotal", money(s.subtotal()), width));
      p.line(col("Discount", "-" + money(s.discountTotal()), width));
    }
    p.bold(true).line(col("TOTAL KES", money(s.total()), width)).bold(false);
    for (VatLine v : r.vatBreakdown()) {
      p.line(
          col(
              v.code()
                  + " VAT "
                  + v.ratePercent().stripTrailingZeros().toPlainString()
                  + "%"
                  + " on "
                  + money(v.taxable()),
              money(v.vat()),
              width));
    }
    p.line(rule);
    for (PaymentView pay : s.payments()) {
      String ref = pay.reference() == null ? "" : " " + pay.reference();
      p.line(col(pay.label() + ref, money(pay.amount()), width));
    }
    if (s.changeDue().signum() > 0) {
      p.line(col("Change", money(s.changeDue()), width));
    }
    p.line(rule).align(Align.CENTER);
    r.paymentInstructions().forEach(p::line);
    if (r.returnPolicy() != null && !r.returnPolicy().isBlank()) {
      wrap(r.returnPolicy(), width).forEach(p::line);
    }
    etims(p, r.etims(), width);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(p.build());
    if (r.etims() != null && r.etims().verifyUrl() != null) {
      out.writeBytes(qrCode(r.etims().verifyUrl()));
    }
    EscPosBuilder end = new EscPosBuilder().align(Align.CENTER);
    r.footer().forEach(end::line);
    out.writeBytes(end.feed(3).cut().build());
    return out.toByteArray();
  }

  private static String col(String left, String right, int width) {
    return ReceiptRenderer.columns(left, right, width);
  }

  private static String fit(String text, int width) {
    return text.length() > width ? text.substring(0, width) : text;
  }

  /** The KRA eTIMS block: signed invoice details, or a note that it is on its way. */
  private static void etims(EscPosBuilder p, EtimsService.ReceiptInfo e, int width) {
    if (e == null) {
      return;
    }
    p.line("-".repeat(width)).align(Align.LEFT);
    if (!"SENT".equals(e.status())) {
      p.line("KRA eTIMS invoice " + e.invoiceNo() + ": being sent to KRA");
      return;
    }
    p.bold(true).line("KRA eTIMS").bold(false);
    p.line(col("Invoice no.", String.valueOf(e.invoiceNo()), width));
    p.line(col("SCU ID", nullToEmpty(e.scuId()), width));
    p.line(col("Receipt no.", e.scuReceiptNo() + "/" + e.totalReceiptNo(), width));
    p.line(col("Date", nullToEmpty(e.scuDate()), width));
    p.line("Internal data: " + nullToEmpty(e.internalData()));
    p.line("Signature: " + nullToEmpty(e.signature()));
    p.align(Align.CENTER);
  }

  /** ESC/POS QR code (GS ( k), size 6, error correction L - for the KRA check link. */
  static byte[] qrCode(String data) {
    byte[] text = data.getBytes(StandardCharsets.US_ASCII);
    int len = text.length + 3;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {0x1B, 0x61, 0x01}); // centre
    out.writeBytes(new byte[] {0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00}); // model 2
    out.writeBytes(new byte[] {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06}); // size
    out.writeBytes(new byte[] {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30}); // error level L
    out.writeBytes(
        new byte[] {0x1D, 0x28, 0x6B, (byte) (len % 256), (byte) (len / 256), 0x31, 0x50, 0x30});
    out.writeBytes(text);
    out.writeBytes(new byte[] {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30}); // print
    out.writeBytes(new byte[] {0x0A});
    return out.toByteArray();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /** Splits text into lines of at most width characters, breaking between words. */
  static List<String> wrap(String text, int width) {
    List<String> lines = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String word : text.trim().split("\\s+")) {
      if (!current.isEmpty() && current.length() + 1 + word.length() > width) {
        lines.add(current.toString());
        current.setLength(0);
      }
      if (!current.isEmpty()) {
        current.append(' ');
      }
      current.append(fit(word, width));
    }
    if (!current.isEmpty()) {
      lines.add(current.toString());
    }
    return lines;
  }

  static String money(BigDecimal amount) {
    return new DecimalFormat("#,##0.00").format(amount);
  }
}
