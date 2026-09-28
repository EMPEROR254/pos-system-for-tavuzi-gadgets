package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SerialView;
import org.junit.jupiter.api.Test;

class ReceiptLayoutTest {

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  private final LineView phone =
      new LineView(
          1,
          "Tecno Spark 20",
          "Piece",
          1,
          0,
          d("11600.00"),
          d("600.00"),
          d("11000.00"),
          "B",
          d("16.00"),
          d("1517.24"),
          Instant.parse("2027-09-28T07:00:00Z"),
          List.of(new SerialView("356938035643809", false)));
  private final LineView cable =
      new LineView(
          2,
          "USB cable",
          "Piece",
          2,
          0,
          d("200.00"),
          d("0.00"),
          d("400.00"),
          "A",
          d("0.00"),
          d("0.00"),
          null,
          List.of());

  private final SaleView sale =
      new SaleView(
          9,
          "TG000009",
          Instant.parse("2026-09-28T07:00:00Z"),
          "Mary Wanjiru",
          "COMPLETED",
          "RETAIL",
          null,
          null,
          d("12000.00"),
          d("600.00"),
          d("1517.24"),
          d("11400.00"),
          d("11500.00"),
          d("100.00"),
          List.of(phone, cable),
          List.of(
              new PaymentView("MPESA_TILL", "M-Pesa Till", d("10000.00"), "SJK4H7XYZ1"),
              new PaymentView("CASH", "Cash", d("1500.00"), null)));

  private final ShopProperties shop =
      new ShopProperties(
          "TAVUZI GADGETS",
          "Resma Plaza, Nakuru",
          "0712345678",
          "P051234567X",
          "123456",
          "654321",
          "TAVUZI",
          "No returns without the owner's approval.",
          List.of("Asante!"));

  @Test
  void vatIsBrokenDownPerTaxCode() {
    var lines = ReceiptService.vatBreakdown(List.of(phone, cable));
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).code()).isEqualTo("A");
    assertThat(lines.get(1).taxable()).isEqualByComparingTo("9482.76");
    assertThat(lines.get(1).vat()).isEqualByComparingTo("1517.24");
  }

  @Test
  void printedReceiptShowsWhatTheSpecAsksFor() {
    ReceiptView view =
        new ReceiptService(null, shop, new PrinterProperties(null, null, null)).build(sale);
    String text = new String(EscPosReceipt.render(view, 48), StandardCharsets.US_ASCII);

    assertThat(text)
        .contains("TG000009")
        .contains("KRA PIN: P051234567X")
        .contains("M-Pesa Buy Goods Till: 123456")
        .contains("M-Pesa Paybill: 654321 Acc: TAVUZI")
        .contains("No returns without the owner's")
        .contains("IMEI/SN: 356938035643809")
        .contains("Warranty until 28/09/2027")
        .contains("M-Pesa Till SJK4H7XYZ1")
        .contains("Change")
        .contains("B VAT 16% on 9,482.76");
  }

  @Test
  void longTextWrapsBetweenWords() {
    assertThat(EscPosReceipt.wrap("No returns or exchanges without approval", 16))
        .containsExactly("No returns or", "exchanges", "without approval");
  }
}
