package ke.bscodes.possystemfortavuzigadgetsbackend.payments;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaStatementParser.StatementRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M-Pesa reconciliation. Cashiers type the M-Pesa code from the customer's SMS; the owner imports
 * the M-Pesa statement, and every typed code is checked against it:
 *
 * <ul>
 *   <li>MATCHED - the money arrived, same amount;
 *   <li>AMOUNT_DIFFERS - the code exists but the amount is different;
 *   <li>NOT_IN_STATEMENT - no such money in the statement (a typo, or a fake SMS);
 *   <li>NOT_IN_SYSTEM - money arrived but no sale uses its code (not recorded at the till).
 * </ul>
 */
@Service
public class MpesaReconciliationService {

  public enum Result {
    MATCHED,
    AMOUNT_DIFFERS,
    NOT_IN_STATEMENT,
    NOT_IN_SYSTEM
  }

  public record ImportResult(int rows, int added, int alreadyImported, Instant from, Instant to) {}

  public record Line(
      Result result,
      String code,
      Instant time,
      BigDecimal systemAmount,
      BigDecimal statementAmount,
      String receiptNo,
      Long saleId,
      String cashierName,
      String otherParty) {}

  public record Report(
      BigDecimal systemTotal,
      BigDecimal statementTotal,
      int matched,
      int problems,
      boolean statementImported,
      List<Line> lines) {}

  /** An M-Pesa code typed at the till. */
  private record Typed(
      String code,
      BigDecimal amount,
      Instant time,
      String receiptNo,
      long saleId,
      String cashier) {}

  /** Money received, from the imported statement. */
  private record Received(String code, BigDecimal amount, Instant time, String otherParty) {}

  private final JdbcClient jdbc;
  private final Clock clock;

  public MpesaReconciliationService(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Adds new statement lines; lines already imported (same receipt) are skipped. */
  @Transactional
  public ImportResult importStatement(String csv, long userId) {
    List<StatementRow> rows = MpesaStatementParser.parse(csv);
    Instant now = clock.instant();
    int added = 0;
    for (StatementRow r : rows) {
      added +=
          jdbc.sql(
                  """
                  insert into mpesa_transactions
                    (receipt_no, completed_at, amount, other_party, account, details,
                     imported_by, imported_at)
                  values (:code, :time, :amount, :other, :account, :details, :user, :now)
                  on conflict (receipt_no) do nothing
                  """)
              .param("code", r.receiptNo())
              .param("time", Timestamp.from(r.completedAt()))
              .param("amount", r.amount())
              .param("other", r.otherParty())
              .param("account", r.account())
              .param("details", r.details())
              .param("user", userId)
              .param("now", Timestamp.from(now))
              .update();
    }
    Instant from =
        rows.stream().map(StatementRow::completedAt).min(Comparator.naturalOrder()).orElse(null);
    Instant to =
        rows.stream().map(StatementRow::completedAt).max(Comparator.naturalOrder()).orElse(null);
    return new ImportResult(rows.size(), added, rows.size() - added, from, to);
  }

  /** Reconciles M-Pesa payments and statement lines between from (inclusive) and to (exclusive). */
  @Transactional(readOnly = true)
  public Report reconcile(Instant from, Instant to) {

    // Typed codes in the period, and statement lines in the period. Each side is then
    // looked up in the other across all dates, so a payment just after midnight still matches.
    List<Typed> typed =
        jdbc.sql(
                """
                select p.reference, p.amount, p.created_at, s.receipt_no, s.id, u.full_name
                from payments p join sales s on s.id = p.sale_id join users u on u.id = s.cashier_id
                where p.method in ('MPESA_TILL', 'MPESA_PAYBILL')
                  and p.created_at >= :from and p.created_at < :to
                """)
            .param("from", Timestamp.from(from))
            .param("to", Timestamp.from(to))
            .query(
                (rs, i) ->
                    new Typed(
                        rs.getString(1),
                        rs.getBigDecimal(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getString(4),
                        rs.getLong(5),
                        rs.getString(6)))
            .list();
    List<Received> received =
        jdbc.sql(
                """
                select receipt_no, amount, completed_at, other_party from mpesa_transactions
                where completed_at >= :from and completed_at < :to
                """)
            .param("from", Timestamp.from(from))
            .param("to", Timestamp.from(to))
            .query(
                (rs, i) ->
                    new Received(
                        rs.getString(1),
                        rs.getBigDecimal(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getString(4)))
            .list();

    Map<String, Received> statementByCode = new HashMap<>();
    received.forEach(r -> statementByCode.put(r.code(), r));
    lookupOutside(
            typed.stream().map(Typed::code).filter(c -> !statementByCode.containsKey(c)).toList())
        .forEach(r -> statementByCode.put(r.code(), r));
    Set<String> typedCodes = new HashSet<>(typed.stream().map(Typed::code).toList());
    typedCodes.addAll(
        codesInSystem(
            received.stream().map(Received::code).filter(c -> !typedCodes.contains(c)).toList()));

    List<Line> lines = new ArrayList<>();
    for (Typed t : typed) {
      Received r = statementByCode.get(t.code());
      Result result =
          r == null
              ? Result.NOT_IN_STATEMENT
              : r.amount().compareTo(t.amount()) == 0 ? Result.MATCHED : Result.AMOUNT_DIFFERS;
      lines.add(
          new Line(
              result,
              t.code(),
              t.time(),
              t.amount(),
              r == null ? null : r.amount(),
              t.receiptNo(),
              t.saleId(),
              t.cashier(),
              r == null ? null : r.otherParty()));
    }
    for (Received r : received) {
      if (!typedCodes.contains(r.code())) {
        lines.add(
            new Line(
                Result.NOT_IN_SYSTEM,
                r.code(),
                r.time(),
                null,
                r.amount(),
                null,
                null,
                null,
                r.otherParty()));
      }
    }
    // Problems first, then by time.
    lines.sort(
        Comparator.comparing((Line l) -> l.result() == Result.MATCHED).thenComparing(Line::time));
    BigDecimal systemTotal =
        typed.stream().map(Typed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal statementTotal =
        received.stream().map(Received::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    int matched = (int) lines.stream().filter(l -> l.result() == Result.MATCHED).count();
    return new Report(
        systemTotal.setScale(2),
        statementTotal.setScale(2),
        matched,
        lines.size() - matched,
        !received.isEmpty(),
        lines);
  }

  private List<Received> lookupOutside(List<String> codes) {
    if (codes.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "select receipt_no, amount, completed_at, other_party from mpesa_transactions"
                + " where receipt_no in (:codes)")
        .param("codes", codes)
        .query(
            (rs, i) ->
                new Received(
                    rs.getString(1),
                    rs.getBigDecimal(2),
                    rs.getTimestamp(3).toInstant(),
                    rs.getString(4)))
        .list();
  }

  private List<String> codesInSystem(List<String> codes) {
    if (codes.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "select reference from payments where method in ('MPESA_TILL', 'MPESA_PAYBILL')"
                + " and reference in (:codes)")
        .param("codes", codes)
        .query(String.class)
        .list();
  }
}
