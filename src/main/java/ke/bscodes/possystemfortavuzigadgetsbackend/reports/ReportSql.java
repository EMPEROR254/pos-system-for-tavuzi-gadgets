package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Runs report SQL. Every report is added up in PostgreSQL (never row by row in Java); the SQL
 * column names match the report's column keys, so rows are read generically by column type.
 */
@Component
public class ReportSql {

  /** Days are the shop's days, so dates are taken in Nairobi time. */
  static final String DAY_OF = "date(%s at time zone 'Africa/Nairobi')";

  /**
   * What was sold per product in the period, less what came back. Returns count on the day they
   * were made (like a KRA credit note), sales on the day of the sale. Quantities are in the
   * smallest unit (pieces), since a product can be sold by the piece or by the box.
   */
  static final String NET_BY_PRODUCT =
      """
      sold as (
        select l.product_id, sum(l.quantity * l.unit_factor) qty, sum(l.line_total) sales,
               sum(l.vat_amount) vat, sum(l.unit_cost * l.quantity) cost
        from sale_lines l join sales s on s.id = l.sale_id
        where s.created_at >= :start and s.created_at < :end
        group by l.product_id),
      ret as (
        select l.product_id, sum(rl.quantity * l.unit_factor) qty, sum(rl.amount) sales,
               sum(rl.vat_amount) vat, sum(rl.quantity * l.unit_cost) cost
        from sale_return_lines rl
        join sale_returns r on r.id = rl.return_id
        join sale_lines l on l.id = rl.sale_line_id
        where r.created_at >= :start and r.created_at < :end
        group by l.product_id),
      net as (
        select coalesce(sold.product_id, ret.product_id) product_id,
               coalesce(sold.qty, 0) - coalesce(ret.qty, 0) qty,
               coalesce(sold.sales, 0) - coalesce(ret.sales, 0) sales,
               coalesce(sold.vat, 0) - coalesce(ret.vat, 0) vat,
               coalesce(sold.cost, 0) - coalesce(ret.cost, 0) cost
        from sold full join ret on ret.product_id = sold.product_id)
      """;

  private final JdbcClient jdbc;

  public ReportSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, ReportRange range, List<Column> columns) {
    return jdbc.sql(sql)
        .param("start", Timestamp.from(range.start()))
        .param("end", Timestamp.from(range.end()))
        .query((rs, n) -> row(rs, columns))
        .list();
  }

  /** One number from a query (counts and sums for the dashboard). */
  public <T> T value(String sql, ReportRange range, Class<T> type) {
    return jdbc.sql(sql)
        .param("start", Timestamp.from(range.start()))
        .param("end", Timestamp.from(range.end()))
        .query(type)
        .single();
  }

  private static Map<String, Object> row(ResultSet rs, List<Column> columns) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    for (Column c : columns) {
      String key = c.key();
      Object value =
          switch (c.kind()) {
            case TEXT -> rs.getString(key);
            case INT -> rs.getLong(key);
            case MONEY -> scale(rs.getBigDecimal(key), 2);
            case PERCENT -> scale(rs.getBigDecimal(key), 1);
            case DATE -> rs.getObject(key, LocalDate.class);
            case DATETIME -> {
              Timestamp t = rs.getTimestamp(key);
              yield t == null ? null : t.toInstant();
            }
          };
      row.put(key, value);
    }
    return row;
  }

  private static BigDecimal scale(BigDecimal v, int digits) {
    return v == null ? null : v.setScale(digits, RoundingMode.HALF_UP);
  }
}
