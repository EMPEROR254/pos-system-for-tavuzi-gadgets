package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.count;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.money;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.of;
import static ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column.text;

import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;
import org.springframework.stereotype.Component;

/**
 * Sales reports: daily summary, profit per product, sales per cashier and VAT. Money is VAT
 * inclusive (shelf prices) unless the column says "excl. VAT"; profit is always excl. VAT.
 */
@Component
public class SalesReports {

  static final List<Column> DAILY =
      List.of(
          of("day", "Day", Kind.DATE),
          count("sales_count", "Sales"),
          money("sales", "Sold"),
          money("discounts", "Discounts"),
          money("refunds", "Returns"),
          money("net_sales", "Net sales"),
          money("vat", "VAT"),
          money("profit", "Profit"),
          money("cash", "Cash"),
          money("mpesa", "M-Pesa"),
          money("card", "Card"));

  private static final String DAILY_SQL =
      """
      with sd as (
        select %1$s as day, count(*) sales_count, sum(s.total) sales,
               sum(s.discount_total) discounts, sum(s.vat_total) vat, sum(s.change_due) change_due
        from sales s where s.created_at >= :start and s.created_at < :end group by 1),
      cd as (
        select %1$s as day, sum(l.unit_cost * l.quantity) cost
        from sale_lines l join sales s on s.id = l.sale_id
        where s.created_at >= :start and s.created_at < :end group by 1),
      pd as (
        select %1$s as day,
               coalesce(sum(p.amount) filter (where p.method = 'CASH'), 0) cash_paid,
               coalesce(sum(p.amount) filter (where p.method in ('MPESA_TILL', 'MPESA_PAYBILL')), 0) mpesa,
               coalesce(sum(p.amount) filter (where p.method = 'CARD'), 0) card
        from payments p join sales s on s.id = p.sale_id
        where s.created_at >= :start and s.created_at < :end group by 1),
      rd as (
        select %2$s as day, sum(rl.amount) refunds, sum(rl.vat_amount) refund_vat,
               sum(rl.quantity * l.unit_cost) refund_cost
        from sale_return_lines rl
        join sale_returns r on r.id = rl.return_id
        join sale_lines l on l.id = rl.sale_line_id
        where r.created_at >= :start and r.created_at < :end group by 1),
      days as (select day from sd union select day from rd)
      select d.day,
             coalesce(sd.sales_count, 0) sales_count,
             coalesce(sd.sales, 0) sales,
             coalesce(sd.discounts, 0) discounts,
             coalesce(rd.refunds, 0) refunds,
             coalesce(sd.sales, 0) - coalesce(rd.refunds, 0) net_sales,
             coalesce(sd.vat, 0) - coalesce(rd.refund_vat, 0) vat,
             (coalesce(sd.sales, 0) - coalesce(sd.vat, 0) - coalesce(cd.cost, 0))
               - (coalesce(rd.refunds, 0) - coalesce(rd.refund_vat, 0) - coalesce(rd.refund_cost, 0)) profit,
             coalesce(pd.cash_paid, 0) - coalesce(sd.change_due, 0) cash,
             coalesce(pd.mpesa, 0) mpesa,
             coalesce(pd.card, 0) card
      from days d
      left join sd on sd.day = d.day
      left join cd on cd.day = d.day
      left join pd on pd.day = d.day
      left join rd on rd.day = d.day
      order by d.day
      """
          .formatted(
              ReportSql.DAY_OF.formatted("s.created_at"),
              ReportSql.DAY_OF.formatted("r.created_at"));

  static final List<Column> PRODUCT_PROFIT =
      List.of(
          text("product", "Product"),
          text("category", "Category"),
          count("quantity", "Qty"),
          money("sales", "Sales"),
          money("net_sales", "Sales excl. VAT"),
          money("cost", "Cost"),
          money("profit", "Profit"),
          of("margin", "Margin %", Kind.PERCENT));

  private static final String PRODUCT_PROFIT_SQL =
      "with "
          + ReportSql.NET_BY_PRODUCT
          + """
          select p.name product, p.category, n.qty quantity, n.sales, n.sales - n.vat net_sales, n.cost,
                 n.sales - n.vat - n.cost profit,
                 case when n.sales - n.vat > 0
                      then (n.sales - n.vat - n.cost) * 100 / (n.sales - n.vat) end margin
          from net n join products p on p.id = n.product_id
          order by profit desc, p.name
          """;

  static final List<Column> CASHIERS =
      List.of(
          text("cashier", "Cashier"),
          count("sales_count", "Sales"),
          money("sales", "Sold"),
          new Column("average", "Average sale", Kind.MONEY, false),
          money("discounts", "Discounts"),
          money("refunds", "Returns handled"),
          count("shifts", "Shifts closed"),
          money("cash_difference", "Cash short/over"));

  private static final String CASHIERS_SQL =
      """
      with sc as (
        select s.cashier_id uid, count(*) n, sum(s.total) total, sum(s.discount_total) disc
        from sales s where s.created_at >= :start and s.created_at < :end group by 1),
      rc as (
        select r.processed_by uid, sum(r.refund_amount) refunds
        from sale_returns r where r.created_at >= :start and r.created_at < :end group by 1),
      shc as (
        select sh.cashier_id uid, count(*) shifts, sum(sh.difference) diff
        from shifts sh where sh.closed_at >= :start and sh.closed_at < :end group by 1),
      ids as (select uid from sc union select uid from rc union select uid from shc)
      select u.full_name cashier,
             coalesce(sc.n, 0) sales_count,
             coalesce(sc.total, 0) sales,
             case when sc.n > 0 then sc.total / sc.n else 0 end average,
             coalesce(sc.disc, 0) discounts,
             coalesce(rc.refunds, 0) refunds,
             coalesce(shc.shifts, 0) shifts,
             coalesce(shc.diff, 0) cash_difference
      from ids
      join users u on u.id = ids.uid
      left join sc on sc.uid = ids.uid
      left join rc on rc.uid = ids.uid
      left join shc on shc.uid = ids.uid
      order by sales desc, u.full_name
      """;

  static final List<Column> VAT =
      List.of(
          text("vat_class", "VAT class"),
          money("sales_net", "Sales excl. VAT"),
          money("sales_vat", "VAT on sales"),
          money("returns_net", "Returns excl. VAT"),
          money("returns_vat", "VAT on returns"),
          money("taxable", "Net excl. VAT"),
          money("vat_payable", "VAT payable"));

  private static final String VAT_SQL =
      """
      with sv as (
        select l.vat_category cat, l.vat_rate rate, sum(l.line_total) gross, sum(l.vat_amount) vat
        from sale_lines l join sales s on s.id = l.sale_id
        where s.created_at >= :start and s.created_at < :end group by 1, 2),
      rv as (
        select l.vat_category cat, l.vat_rate rate, sum(rl.amount) gross, sum(rl.vat_amount) vat
        from sale_return_lines rl
        join sale_returns r on r.id = rl.return_id
        join sale_lines l on l.id = rl.sale_line_id
        where r.created_at >= :start and r.created_at < :end group by 1, 2)
      select case coalesce(sv.cat, rv.cat)
               when 'STANDARD' then 'Standard rated '
                    || rtrim(rtrim(coalesce(sv.rate, rv.rate)::text, '0'), '.') || '%'
               when 'ZERO_RATED' then 'Zero rated'
               else 'Exempt' end vat_class,
             coalesce(sv.gross, 0) - coalesce(sv.vat, 0) sales_net,
             coalesce(sv.vat, 0) sales_vat,
             coalesce(rv.gross, 0) - coalesce(rv.vat, 0) returns_net,
             coalesce(rv.vat, 0) returns_vat,
             (coalesce(sv.gross, 0) - coalesce(sv.vat, 0)) - (coalesce(rv.gross, 0) - coalesce(rv.vat, 0)) taxable,
             coalesce(sv.vat, 0) - coalesce(rv.vat, 0) vat_payable
      from sv full join rv on rv.cat = sv.cat and rv.rate = sv.rate
      order by 1
      """;

  private final ReportSql sql;

  public SalesReports(ReportSql sql) {
    this.sql = sql;
  }

  public List<Map<String, Object>> daily(ReportRange range) {
    return sql.rows(DAILY_SQL, range, DAILY);
  }

  public List<Map<String, Object>> productProfit(ReportRange range) {
    return sql.rows(PRODUCT_PROFIT_SQL, range, PRODUCT_PROFIT);
  }

  public List<Map<String, Object>> cashiers(ReportRange range) {
    return sql.rows(CASHIERS_SQL, range, CASHIERS);
  }

  public List<Map<String, Object>> vat(ReportRange range) {
    return sql.rows(VAT_SQL, range, VAT);
  }
}
