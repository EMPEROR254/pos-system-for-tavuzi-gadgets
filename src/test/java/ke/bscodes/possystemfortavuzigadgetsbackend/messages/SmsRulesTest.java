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

  @Test
  void quietHoursRunOverMidnightInNairobi() {
    QuietHours q = new QuietHours(java.time.LocalTime.of(20, 0), java.time.LocalTime.of(8, 0));
    // 21:30 Nairobi = 18:30 UTC.
    Instant evening = Instant.parse("2026-09-28T18:30:00Z");
    assertThat(q.contains(evening)).isTrue();
    assertThat(q.nextAllowed(evening)).isEqualTo(Instant.parse("2026-09-29T05:00:00Z"));
    // 06:00 Nairobi waits until 08:00 the same day.
    Instant early = Instant.parse("2026-09-29T03:00:00Z");
    assertThat(q.nextAllowed(early)).isEqualTo(Instant.parse("2026-09-29T05:00:00Z"));
    // Midday goes straight away.
    Instant noon = Instant.parse("2026-09-29T09:00:00Z");
    assertThat(q.contains(noon)).isFalse();
    assertThat(q.nextAllowed(noon)).isEqualTo(noon);
  }

  @Test
  void onlyFailedOrUndeliveredMessagesCanBeResent() {
    SmsMessage m =
        new SmsMessage(
            "254712345678", "Hi", SmsMessage.Kind.PROMO, null, null, null, Instant.EPOCH);
    assertThat(m.canResend()).isFalse();
    m.recordAttempt(SmsSender.Result.ok("ATX1"), Instant.EPOCH);
    m.recordDelivery("Success", null, Instant.EPOCH);
    assertThat(m.canResend()).isFalse();
    m.recordDelivery("Failed", "DeliveryFailure", Instant.EPOCH);
    assertThat(m.canResend()).isTrue();
    m.resend();
    assertThat(m.getStatus()).isEqualTo(SmsMessage.Status.QUEUED);
    assertThat(m.getDeliveryStatus()).isNull();
  }

  @Test
  void stopRepliesAreRecognised() {
    assertThat(MessageService.isStop(" stop ")).isTrue();
    assertThat(MessageService.isStop("STOP promotions")).isTrue();
    assertThat(MessageService.isStop("Unsubscribe")).isTrue();
    assertThat(MessageService.isStop("Stopwatch price?")).isFalse();
    assertThat(MessageService.isStop("")).isFalse();
  }
}
