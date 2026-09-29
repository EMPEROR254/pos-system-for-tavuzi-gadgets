package ke.bscodes.possystemfortavuzigadgetsbackend.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaStatementParser.StatementRow;
import org.junit.jupiter.api.Test;

class MpesaStatementParserTest {

  /** Shaped like the M-Pesa business portal export: account lines first, then the table. */
  static final String STATEMENT =
      """
      Organization Name,TAVUZI GADGETS
      Account,Till 123456
      Time Period,01-09-2026 - 30-09-2026

      Receipt No.,Completion Time,Initiation Time,Details,Transaction Status,Paid In,Withdrawn,Balance,Balance Confirmed,Reason Type,Other Party Info,Linked Transaction ID,A/C No.
      SJK4H7XYZ1,2026-09-28 10:15:32,2026-09-28 10:15:30,Pay Bill Online,Completed,"12,000.00",,150000.00,true,Pay Bill Online,254712***678 - GRACE CHEBET,,TAVUZI
      SJK4H7XYZ2,2026-09-28 11:00:00,2026-09-28 11:00:00,Withdraw to bank,Completed,,5000.00,145000.00,true,Settlement,BANK,,
      SJK4H7XYZ3,2026-09-28 12:00:00,2026-09-28 12:00:00,Merchant Payment,Failed,300.00,,145000.00,true,Merchant Payment,254722***111 - JOHN,,
      SJK4H7XYZ4,28-09-2026 13:30:00,28-09-2026 13:30:00,Merchant Payment,Completed,650.00,,145650.00,true,Merchant Payment,"254733***222 - ""MAMA"" NJERI",,
      """;

  @Test
  void keepsOnlyMoneyReceivedOnCompletedTransactions() {
    List<StatementRow> rows = MpesaStatementParser.parse(STATEMENT);
    assertThat(rows)
        .extracting(StatementRow::receiptNo)
        .containsExactly("SJK4H7XYZ1", "SJK4H7XYZ4");
    StatementRow first = rows.getFirst();
    assertThat(first.amount()).isEqualByComparingTo("12000.00");
    // Statement times are Kenyan time.
    assertThat(first.completedAt()).isEqualTo(Instant.parse("2026-09-28T07:15:32Z"));
    assertThat(first.account()).isEqualTo("TAVUZI");
    assertThat(rows.get(1).otherParty()).isEqualTo("254733***222 - \"MAMA\" NJERI");
    assertThat(rows.get(1).completedAt()).isEqualTo(Instant.parse("2026-09-28T10:30:00Z"));
  }

  @Test
  void explainsWhenTheFileIsNotAStatement() {
    assertThatThrownBy(() -> MpesaStatementParser.parse("name,phone\nGrace,0712"))
        .hasMessageContaining("doesn't look like an M-Pesa statement");
  }
}
