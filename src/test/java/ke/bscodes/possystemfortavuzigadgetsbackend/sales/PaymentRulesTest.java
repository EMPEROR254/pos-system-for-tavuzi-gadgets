package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentRequest;
import org.junit.jupiter.api.Test;

class PaymentRulesTest {

  private static PaymentRequest pay(PaymentMethod method, String amount, String ref) {
    return new PaymentRequest(method, new BigDecimal(amount), ref);
  }

  private static final BigDecimal TOTAL = new BigDecimal("1500.00");

  @Test
  void splitCashAndMpesaGivesChangeOnCash() {
    var plan =
        PaymentRules.check(
            List.of(
                pay(PaymentMethod.MPESA_TILL, "1000", " sjk4h7xyz1 "),
                pay(PaymentMethod.CASH, "1000", null)),
            TOTAL);
    assertThat(plan.paid()).isEqualByComparingTo("2000");
    assertThat(plan.change()).isEqualByComparingTo("500");
    assertThat(plan.payments().getFirst().reference()).isEqualTo("SJK4H7XYZ1");
  }

  @Test
  void noChangeOnMpesaOrCard() {
    assertThatThrownBy(
            () -> PaymentRules.check(List.of(pay(PaymentMethod.CARD, "2000", "123456")), TOTAL))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Change is only given on cash");
  }

  @Test
  void mustCoverTheTotal() {
    assertThatThrownBy(
            () -> PaymentRules.check(List.of(pay(PaymentMethod.CASH, "1000", null)), TOTAL))
        .hasMessageContaining("500.00 is still to be paid");
  }

  @Test
  void mpesaCodesMustLookRightAndNotRepeat() {
    assertThatThrownBy(
            () ->
                PaymentRules.check(List.of(pay(PaymentMethod.MPESA_PAYBILL, "1500", "ABC")), TOTAL))
        .hasMessageContaining("10-character M-Pesa code");
    assertThatThrownBy(
            () ->
                PaymentRules.check(
                    List.of(
                        pay(PaymentMethod.MPESA_TILL, "700", "SJK4H7XYZ1"),
                        pay(PaymentMethod.MPESA_PAYBILL, "800", "sjk4h7xyz1")),
                    TOTAL))
        .hasMessageContaining("entered twice");
  }
}
