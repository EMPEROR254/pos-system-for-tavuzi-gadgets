package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StkCallbackTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void readsASuccessfulPayment() throws Exception {
    String body =
        """
        {"Body":{"stkCallback":{"MerchantRequestID":"29115-34620561-1",
          "CheckoutRequestID":"ws_CO_191220191020363925","ResultCode":0,
          "ResultDesc":"The service request is processed successfully.",
          "CallbackMetadata":{"Item":[{"Name":"Amount","Value":1.00},
            {"Name":"MpesaReceiptNumber","Value":"NLJ7RT61SV"},
            {"Name":"TransactionDate","Value":20191219102115},
            {"Name":"PhoneNumber","Value":254708374149}]}}}}
        """;
    StkCallback callback = StkCallback.parse(json.readTree(body));
    assertThat(callback.successful()).isTrue();
    assertThat(callback.amount()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(callback.receiptNumber()).isEqualTo("NLJ7RT61SV");
    assertThat(callback.phone()).isEqualTo("254708374149");
    assertThat(callback.transactionDate()).isEqualTo("20191219102115");
  }

  @Test
  void readsACancelledPayment() throws Exception {
    String body =
        """
        {"Body":{"stkCallback":{"MerchantRequestID":"m","CheckoutRequestID":"c",
          "ResultCode":1032,"ResultDesc":"Request cancelled by user"}}}
        """;
    StkCallback callback = StkCallback.parse(json.readTree(body));
    assertThat(callback.successful()).isFalse();
    assertThat(callback.resultCode()).isEqualTo(1032);
    assertThat(callback.amount()).isNull();
  }
}
