package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import org.junit.jupiter.api.Test;

class SmsRulesTest {

  @Test
  void countsSmsPartsLikeTheNetworkBillsThem() {
    assertThat(SmsText.parts("a".repeat(160))).isEqualTo(1);
    assertThat(SmsText.parts("a".repeat(161))).isEqualTo(2);
    assertThat(SmsText.parts("a".repeat(306))).isEqualTo(2);
    // An emoji switches the whole SMS to the 70-character format.
    assertThat(SmsText.parts("Offer 🎉 " + "a".repeat(70))).isEqualTo(2);
  }

  @Test
  void failedSendsAreRetriedThenMarkedFailed() {
    SmsMessage m =
        new SmsMessage(
            "254712345678", "Hi", SmsMessage.Kind.PROMO, null, null, null, Instant.EPOCH);
    m.recordAttempt(SmsSender.Result.failed("timeout"), Instant.EPOCH);
    assertThat(m.getStatus()).isEqualTo(SmsMessage.Status.QUEUED);
    m.recordAttempt(SmsSender.Result.failed("timeout"), Instant.EPOCH);
    m.recordAttempt(SmsSender.Result.failed("timeout"), Instant.EPOCH);
    assertThat(m.getStatus()).isEqualTo(SmsMessage.Status.FAILED);
  }

  @Test
  void receiptSmsNamesTotalPaymentAndWarranty() {
    BigDecimal z = BigDecimal.ZERO;
    BigDecimal total = new BigDecimal("12000");
    LineView phone =
        new LineView(
            1,
            "Tecno Spark 20",
            "Piece",
            1,
            0,
            total,
            z,
            total,
            "B",
            new BigDecimal("16"),
            z,
            Instant.parse("2027-09-28T07:00:00Z"),
            List.of());
    SaleView sale =
        new SaleView(
            1,
            "TG000042",
            Instant.parse("2026-09-28T07:00:00Z"),
            "Mary",
            "COMPLETED",
            "RETAIL",
            "Grace",
            "254712345678",
            total,
            z,
            z,
            total,
            total,
            z,
            List.of(phone),
            List.of(new PaymentView("MPESA_TILL", "M-Pesa Till", total, "SJK4H7XYZ1")));
    assertThat(MessageService.receiptText(sale, "TAVUZI GADGETS"))
        .isEqualTo(
            "TAVUZI GADGETS receipt TG000042: KES 12,000 paid by M-Pesa Till SJK4H7XYZ1."
                + " Tecno Spark 20 warranty to 28/09/2027. Thank you!");
    assertThat(LoggingSmsSender.mask("254712345678")).isEqualTo("2547****5678");
  }
}
