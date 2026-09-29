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

/** Stock reports: what the stock is worth, and what sells fast or sits on the shelf. */
@Component
public class StockReports {

  static final int BEST_LIMIT = 20;
  static final int SLOW_LIMIT = 30;

  static final List<Column> STOCK_VALUE =
      List.of(
          text("product", "Product"),
          text("category", "Category"),
          count("in_stock", "In stock"),
          new Column("unit_cost", "Cost each", Kind.MONEY, false),
          money("cost_value", "Value at cost"),
          money("retail_value", "Value at retail"),
          money("potential_profit", "Potential profit"));

  /**
   * Retail value uses the cheapest price per piece among the product's units (e.g. the box price
   * divided by the pieces in it), so the figure is never overstated.
   */
  private static final String STOCK_VALUE_SQL =
      """
      select p.name product, p.category, p.stock_on_hand in_stock, p.cost_price unit_cost,
             p.stock_on_hand * p.cost_price cost_value, v.retail_value,
             v.retail_value - p.stock_on_hand * p.cost_price potential_profit
      from products p
      cross join lateral (
        select p.stock_on_hand * coalesce(min(u.retail_price / u.factor), 0) retail_value
        from product_units u where u.product_id = p.id) v
      where p.active and p.stock_on_hand <> 0
      order by cost_value desc, p.name
      """;

  static final List<Column> BEST_SELLERS =
      List.of(
          text("product", "Product"),
          text("category", "Category"),
          count("quantity", "Qty sold"),
          money("sales", "Sales"),
          money("profit", "Profit"),
          count("in_stock", "In stock"));

  private static final String BEST_SELLERS_SQL =
      "with "
          + ReportSql.NET_BY_PRODUCT
          + """
          select p.name product, p.category, n.qty quantity, n.sales, n.sales - n.vat - n.cost profit,
                 p.stock_on_hand in_stock
          from net n join products p on p.id = n.product_id
          where n.qty > 0
          order by n.qty desc, n.sales desc, p.name
          limit %d
          """
              .formatted(BEST_LIMIT);

  static final List<Column> SLOW_SELLERS =
      List.of(
          text("product", "Product"),
          text("category", "Category"),
          count("quantity", "Qty sold"),
          count("in_stock", "In stock"),
          money("stock_value", "Stock value (cost)"),
          of("last_sold", "Last sold", Kind.DATETIME));

  /**
   * Products with stock that sold least in the period (nothing at all first), biggest money first.
   */
  private static final String SLOW_SELLERS_SQL =
      "with "
          + ReportSql.NET_BY_PRODUCT
          + """
          select p.name product, p.category, coalesce(n.qty, 0) quantity, p.stock_on_hand in_stock,
                 p.stock_on_hand * p.cost_price stock_value,
                 (select max(s.created_at) from sale_lines l join sales s on s.id = l.sale_id
                   where l.product_id = p.id) last_sold
          from products p left join net n on n.product_id = p.id
          where p.active and p.stock_on_hand > 0
          order by coalesce(n.qty, 0), stock_value desc, p.name
          limit %d
          """
              .formatted(SLOW_LIMIT);

  private final ReportSql sql;

  public StockReports(ReportSql sql) {
    this.sql = sql;
  }

  public List<Map<String, Object>> stockValue(ReportRange range) {
    return sql.rows(STOCK_VALUE_SQL, range, STOCK_VALUE);
  }

  public List<Map<String, Object>> bestSellers(ReportRange range) {
    return sql.rows(BEST_SELLERS_SQL, range, BEST_SELLERS);
  }

  public List<Map<String, Object>> slowSellers(ReportRange range) {
    return sql.rows(SLOW_SELLERS_SQL, range, SLOW_SELLERS);
  }
}
