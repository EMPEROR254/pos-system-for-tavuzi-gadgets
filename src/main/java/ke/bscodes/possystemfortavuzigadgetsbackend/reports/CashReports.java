package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.money;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.of;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaReconciliationService;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaReconciliationService.Report;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.ShiftView;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftService;
import org.springframework.stereotype.Component;

/** Cash reports built from other features: shift cash-ups and the M-Pesa reconciliation. */
@Component
public class CashReports {

  static final List<Column> SHIFTS =
      List.of(
          text("cashier", "Cashier"),
          of("opened_at", "Opened", Kind.DATETIME),
          of("closed_at", "Closed", Kind.DATETIME),
          money("opening_float", "Float"),
          money("cash_sales", "Cash sales"),
          money("cash_refunds", "Cash refunds"),
          money("expected_cash", "Expected"),
          money("counted_cash", "Counted"),
          money("difference", "Short/over"),
          text("note", "Note"));

  static final List<Column> MPESA =
      List.of(
          text("result", "Result"),
          text("code", "M-Pesa code"),
          of("time", "Time", Kind.DATETIME),
          money("system_amount", "Till"),
          money("statement_amount", "Statement"),
          text("receipt_no", "Receipt"),
          text("cashier", "Cashier"),
          text("other_party", "Paid by"));

  private final ShiftService shifts;
  private final MpesaReconciliationService mpesa;

  public CashReports(ShiftService shifts, MpesaReconciliationService mpesa) {
    this.shifts = shifts;
    this.mpesa = mpesa;
  }

  public List<Map<String, Object>> shifts(ReportRange range) {
    return shifts.list(range.start(), range.end(), null).stream().map(CashReports::row).toList();
  }

  private static Map<String, Object> row(ShiftView s) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("cashier", s.cashierName());
    r.put("opened_at", s.openedAt());
    r.put("closed_at", s.closedAt());
    r.put("opening_float", s.openingFloat());
    r.put("cash_sales", s.cashSales());
    r.put("cash_refunds", s.cashRefunds());
    r.put("expected_cash", s.expectedCash());
    r.put("counted_cash", s.countedCash());
    r.put("difference", s.difference());
    r.put("note", s.closedAt() == null ? "Still open" : s.note());
    return r;
  }

  public Report mpesa(ReportRange range) {
    return mpesa.reconcile(range.start(), range.end());
  }

  public static List<Map<String, Object>> mpesaRows(Report report) {
    return report.lines().stream()
        .map(
            l -> {
              Map<String, Object> r = new LinkedHashMap<>();
              r.put("result", label(l.result()));
              r.put("code", l.code());
              r.put("time", l.time());
              r.put("system_amount", l.systemAmount());
              r.put("statement_amount", l.statementAmount());
              r.put("receipt_no", l.receiptNo());
              r.put("cashier", l.cashierName());
              r.put("other_party", l.otherParty());
              return r;
            })
        .toList();
  }

  private static String label(MpesaReconciliationService.Result result) {
    return switch (result) {
      case MATCHED -> "Matched";
      case AMOUNT_DIFFERS -> "Amount differs";
      case NOT_IN_STATEMENT -> "Not in statement";
      case NOT_IN_SYSTEM -> "No sale";
    };
  }
}
