package ke.bscodes.possystemfortavuzigadgetsbackend.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptRendererTest {

  private final ReceiptRenderer.Receipt sale =
      new ReceiptRenderer.Receipt(
          "Mama Mboga",
          List.of("Nakuru"),
          "R-000123",
          "Wanjiku",
          Instant.parse("2026-09-27T07:30:00Z"),
          List.of(
              new ReceiptRenderer.Line("Sugar 2kg", 1, new BigDecimal("480")),
              new ReceiptRenderer.Line("Maize flour 2kg", 2, new BigDecimal("160"))),
          16,
          "M-Pesa",
          "SJ12ABC34D",
          List.of("Asante! Karibu tena"));

  @Test
  void computesVatIncludedInPrices() {
    assertThat(ReceiptRenderer.total(sale)).isEqualByComparingTo("800");
    assertThat(ReceiptRenderer.vat(sale)).isEqualByComparingTo("110.34");
  }

  @Test
  void padsColumns() {
    assertThat(ReceiptRenderer.columns("Sugar", "480.00", 20)).isEqualTo("Sugar         480.00");
    assertThat(ReceiptRenderer.columns("A very long product name", "1.00", 20)).hasSize(20);
  }

  @Test
  void producesEscPosBytes() {
    byte[] bytes = ReceiptRenderer.render(sale, 32);
    assertThat(bytes[0]).isEqualTo((byte) 0x1B);
    assertThat(bytes[1]).isEqualTo((byte) 0x40);
    assertThat(bytes[bytes.length - 4]).isEqualTo((byte) 0x1D);
    assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
        .contains("SJ12ABC34D")
        .contains("TOTAL KES");
  }
}
