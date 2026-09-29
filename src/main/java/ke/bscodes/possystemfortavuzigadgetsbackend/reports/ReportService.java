package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.asLong;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.asMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaReconciliationService.Report;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.DashboardDtos.Dashboard;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.DashboardDtos.DayPoint;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.DashboardDtos.Kpis;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.DashboardDtos.Slice;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Figure;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds each report as a {@link ReportTable}, and the dashboard from the same numbers. */
@Service
@Transactional(readOnly = true)
public class ReportService {

  private static final String RETURNS_NOTE =
      "Sales count on the day they were made; returns on the day they were brought back.";

  private final SalesReports sales;
  private final StockReports stock;
  private final CashReports cash;
  private final ReportSql sql;

  public ReportService(SalesReports sales, StockReports stock, CashReports cash, ReportSql sql) {
    this.sales = sales;
    this.stock = stock;
    this.cash = cash;
    this.sql = sql;
  }

  public ReportTable table(ReportName name, ReportRange range) {
    return switch (name) {
      case DAILY_SALES -> {
        List<Map<String, Object>> rows = sales.daily(range);
        Map<String, Object> t = ReportTable.totalsOf(SalesReports.DAILY, rows);
        yield build(
            name,
            range,
            RETURNS_NOTE + " Cash is after change given.",
            List.of(
                money("Net sales", t.get("net_sales")),
                new Figure("Sales", t.get("sales_count"), Kind.INT),
                money("Profit", t.get("profit")),
                money("VAT", t.get("vat"))),
            SalesReports.DAILY,
            rows,
            t);
      }
      case PRODUCT_PROFIT -> {
        List<Map<String, Object>> rows = sales.productProfit(range);
        Map<String, Object> t = ReportTable.totalsOf(SalesReports.PRODUCT_PROFIT, rows);
        t.put("margin", margin(asMoney(t.get("profit")), asMoney(t.get("net_sales"))));
        yield build(
            name,
            range,
            RETURNS_NOTE + " Quantities are in pieces. Profit = sales excl. VAT - cost.",
            List.of(money("Profit", t.get("profit")), money("Sales excl. VAT", t.get("net_sales"))),
            SalesReports.PRODUCT_PROFIT,
            rows,
            t);
      }
      case CASHIERS -> {
        List<Map<String, Object>> rows = sales.cashiers(range);
        Map<String, Object> t = ReportTable.totalsOf(SalesReports.CASHIERS, rows);
        long count = asLong(t.get("sales_count"));
        t.put(
            "average",
            count == 0
                ? zero()
                : asMoney(t.get("sales"))
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        yield build(
            name,
            range,
            "Cash short/over is from shifts closed in these dates (minus = short).",
            List.of(
                money("Sold", t.get("sales")), money("Cash short/over", t.get("cash_difference"))),
            SalesReports.CASHIERS,
            rows,
            t);
      }
      case SHIFTS -> {
        List<Map<String, Object>> rows = cash.shifts(range);
        Map<String, Object> t = ReportTable.totalsOf(CashReports.SHIFTS, rows);
        long open = rows.stream().filter(r -> r.get("closed_at") == null).count();
        yield build(
            name,
            range,
            "Shifts opened in these dates. Short/over = counted - expected (minus = short).",
            List.of(
                new Figure("Shifts", rows.size(), Kind.INT),
                new Figure("Still open", open, Kind.INT),
                money("Short/over", t.get("difference"))),
            CashReports.SHIFTS,
            rows,
            t);
      }
      case STOCK_VALUE -> {
        List<Map<String, Object>> rows = stock.stockValue(range);
        Map<String, Object> t = ReportTable.totalsOf(StockReports.STOCK_VALUE, rows);
        yield build(
            name,
            range,
            "Stock on hand right now - the dates don't change this report.",
            List.of(
                money("Value at cost", t.get("cost_value")),
                money("Value at retail", t.get("retail_value"))),
            StockReports.STOCK_VALUE,
            rows,
            t);
      }
      case BEST_SELLERS ->
          build(
              name,
              range,
              "Top " + StockReports.BEST_LIMIT + " by quantity sold (pieces), less returns.",
              List.of(),
              StockReports.BEST_SELLERS,
              stock.bestSellers(range),
              null);
      case SLOW_SELLERS ->
          build(
              name,
              range,
              "Products in stock that sold least in these dates - consider a promotion.",
              List.of(),
              StockReports.SLOW_SELLERS,
              stock.slowSellers(range),
              null);
      case MPESA -> {
        Report r = cash.mpesa(range);
        List<Map<String, Object>> rows = CashReports.mpesaRows(r);
        yield build(
            name,
            range,
            r.statementImported()
                ? "Codes typed at the till checked against the imported M-Pesa statement."
                : "No M-Pesa statement imported for these dates yet - import it under M-Pesa.",
            List.of(
                money("Typed at the till", r.systemTotal()),
                money("In the statement", r.statementTotal()),
                new Figure("Matched", r.matched(), Kind.INT),
                new Figure("To check", r.problems(), Kind.INT)),
            CashReports.MPESA,
            rows,
            null);
      }
      case VAT -> {
        List<Map<String, Object>> rows = sales.vat(range);
        Map<String, Object> t = ReportTable.totalsOf(SalesReports.VAT, rows);
        yield build(
            name,
            range,
            "Prices include VAT. Returns are credit notes, taken off in the period they were made.",
            List.of(
                money("VAT payable", t.get("vat_payable")),
                money("Net sales excl. VAT", t.get("taxable"))),
            SalesReports.VAT,
            rows,
            t);
      }
    };
  }

  private static ReportTable build(
      ReportName name,
      ReportRange range,
      String note,
      List<Figure> summary,
      List<Column> columns,
      List<Map<String, Object>> rows,
      Map<String, Object> totals) {
    return new ReportTable(
        name.slug(), name.title(), period(range), note, summary, columns, rows, totals);
  }

  public Dashboard dashboard(ReportRange range) {
    List<Map<String, Object>> daily = sales.daily(range);
    Map<String, Object> t = ReportTable.totalsOf(SalesReports.DAILY, daily);
    long count = asLong(t.get("sales_count"));
    BigDecimal sold = asMoney(t.get("sales"));

    Kpis kpis =
        new Kpis(
            asMoney(t.get("net_sales")),
            count,
            count == 0 ? zero() : sold.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
            asMoney(t.get("profit")),
            asMoney(t.get("vat")),
            asMoney(t.get("refunds")),
            sql.value(
                    "select coalesce(sum(difference), 0) from shifts"
                        + " where closed_at >= :start and closed_at < :end",
                    range,
                    BigDecimal.class)
                .setScale(2),
            sql.value(
                "select count(*) from products"
                    + " where active and reorder_level > 0 and stock_on_hand <= reorder_level",
                range,
                Long.class),
            sql.value(
                    "select coalesce(sum(stock_on_hand * cost_price), 0) from products"
                        + " where active and stock_on_hand > 0",
                    range,
                    BigDecimal.class)
                .setScale(2, RoundingMode.HALF_UP),
            sql.value("select count(*) from shifts where closed_at is null", range, Long.class));

    List<Slice> payments =
        List.of(
            new Slice("Cash", asMoney(t.get("cash"))),
            new Slice("M-Pesa", asMoney(t.get("mpesa"))),
            new Slice("Card", asMoney(t.get("card"))));

    List<Slice> top =
        sales.productProfit(range).stream()
            .sorted(
                Comparator.comparing((Map<String, Object> r) -> asMoney(r.get("sales"))).reversed())
            .limit(5)
            .map(r -> new Slice((String) r.get("product"), asMoney(r.get("sales"))))
            .toList();

    List<Slice> cashiers =
        sales.cashiers(range).stream()
            .map(r -> new Slice((String) r.get("cashier"), asMoney(r.get("sales"))))
            .toList();

    return new Dashboard(
        range.from(), range.to(), kpis, fillDays(range, daily), payments, top, cashiers);
  }

  /** One point per day, with zeros for days without sales, so the chart's line is honest. */
  static List<DayPoint> fillDays(ReportRange range, List<Map<String, Object>> daily) {
    Map<LocalDate, Map<String, Object>> byDay = new HashMap<>();
    daily.forEach(r -> byDay.put((LocalDate) r.get("day"), r));
    List<DayPoint> points = new ArrayList<>();
    for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
      Map<String, Object> r = byDay.get(d);
      points.add(
          r == null
              ? new DayPoint(d, zero(), zero(), 0)
              : new DayPoint(
                  d,
                  asMoney(r.get("net_sales")),
                  asMoney(r.get("profit")),
                  asLong(r.get("sales_count"))));
    }
    return points;
  }

  static BigDecimal margin(BigDecimal profit, BigDecimal netSales) {
    return netSales.signum() <= 0
        ? null
        : profit.multiply(BigDecimal.valueOf(100)).divide(netSales, 1, RoundingMode.HALF_UP);
  }

  static String period(ReportRange range) {
    return range.from().equals(range.to())
        ? ReportFormat.date(range.from())
        : ReportFormat.date(range.from()) + " - " + ReportFormat.date(range.to());
  }

  private static Figure money(String label, Object value) {
    return new Figure(label, value, Kind.MONEY);
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
