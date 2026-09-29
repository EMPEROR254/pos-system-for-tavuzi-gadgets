package ke.bscodes.possystemfortavuzigadgetsbackend.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M-Pesa end to end: cashiers type codes at the till, the owner imports the M-Pesa statement, and
 * reconciliation shows which codes are real, wrong or missing.
 */
class MpesaReconciliationFlowTest extends ApiTest {

  private static String code(String prefix) {
    return (prefix + String.format("%08d", SEQ.incrementAndGet())).substring(0, 10);
  }

  private void sellWithMpesa(String cashier, JsonNode item, int amount, String code)
      throws Exception {
    Map<String, Object> sale =
        cashSale(
            item.path("id").asLong(),
            item.path("units").get(0).path("id").asLong(),
            1,
            amount,
            null);
    Map<String, Object> pay = new HashMap<>();
    pay.put("method", "MPESA_TILL");
    pay.put("amount", amount);
    pay.put("reference", code);
    sale.put("payments", List.of(pay));
    post(cashier, "/api/sales", sale).andExpect(status().isCreated());
  }

  @Test
  void typedCodesAreCheckedAgainstTheStatement() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode item = createProduct(owner, "Earbuds", "NONE", 1500, null);
    String good = code("SGA");
    String wrongAmount = code("SGB");
    String fake = code("SGC");
    String unrecorded = code("SGD");
    sellWithMpesa(cashier, item, 1500, good);
    sellWithMpesa(cashier, item, 1500, wrongAmount);
    sellWithMpesa(cashier, item, 1500, fake);

    // The test clock is 2026-09-28 07:00Z = 10:00 in Nairobi.
    String statement =
        "Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Other Party Info\n"
            + good
            + ",2026-09-28 10:01:00,Merchant Payment,Completed,1500.00,,GRACE\n"
            + wrongAmount
            + ",2026-09-28 10:02:00,Merchant Payment,Completed,150.00,,JOHN\n"
            + unrecorded
            + ",2026-09-28 10:03:00,Merchant Payment,Completed,800.00,,ANN\n";
    mvc.perform(
            MockMvcRequestBuilders.post("/api/mpesa-reconciliation/statement")
                .header("Authorization", "Bearer " + cashier)
                .contentType(MediaType.TEXT_PLAIN)
                .content(statement))
        .andExpect(status().isForbidden());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/mpesa-reconciliation/statement")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.TEXT_PLAIN)
                .content(statement))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(3));
    // Importing the same statement again adds nothing.
    mvc.perform(
            MockMvcRequestBuilders.post("/api/mpesa-reconciliation/statement")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.TEXT_PLAIN)
                .content(statement))
        .andExpect(jsonPath("$.added").value(0))
        .andExpect(jsonPath("$.alreadyImported").value(3));

    JsonNode report =
        body(
            get(owner, "/api/mpesa-reconciliation?from=2026-09-28&to=2026-09-28")
                .andExpect(status().isOk()));
    assertThat(report.path("statementImported").asBoolean()).isTrue();
    Map<String, String> results = new HashMap<>();
    StreamSupport.stream(report.path("lines").spliterator(), false)
        .forEach(l -> results.put(l.path("code").asText(), l.path("result").asText()));
    assertThat(results)
        .containsEntry(good, "MATCHED")
        .containsEntry(wrongAmount, "AMOUNT_DIFFERS")
        .containsEntry(fake, "NOT_IN_STATEMENT")
        .containsEntry(unrecorded, "NOT_IN_SYSTEM");
  }
}
