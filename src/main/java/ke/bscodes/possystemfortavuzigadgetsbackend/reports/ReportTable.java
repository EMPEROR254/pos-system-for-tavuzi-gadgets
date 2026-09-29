package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One report as a table: typed columns, rows, a totals row and a few headline figures. The same
 * table is sent to the screen as JSON and rendered to PDF or CSV, so the three always agree.
 */
public record ReportTable(
    String name,
    String title,
    String period,
    String note,
    List<Figure> summary,
    List<Column> columns,
    List<Map<String, Object>> rows,
    Map<String, Object> totals) {

  /** How a value is shown: money as "1,250.00", counts as whole numbers, and so on. */
  public enum Kind {
    TEXT,
    INT,
    MONEY,
    PERCENT,
    DATE,
    DATETIME
  }

  /** A column; "sum" columns are added up in the totals row. */
  public record Column(String key, String label, Kind kind, boolean sum) {

    public static Column text(String key, String label) {
      return new Column(key, label, Kind.TEXT, false);
    }

    public static Column money(String key, String label) {
      return new Column(key, label, Kind.MONEY, true);
    }

    public static Column count(String key, String label) {
      return new Column(key, label, Kind.INT, true);
    }

    public static Column of(String key, String label, Kind kind) {
      return new Column(key, label, kind, false);
    }

    public boolean numeric() {
      return kind == Kind.INT || kind == Kind.MONEY || kind == Kind.PERCENT;
    }
  }

  /** A headline number shown above the table (e.g. "VAT payable"). */
  public record Figure(String label, Object value, Kind kind) {}

  /** Adds up the "sum" columns; the first column says "Total". */
  public static Map<String, Object> totalsOf(List<Column> columns, List<Map<String, Object>> rows) {
    Map<String, Object> totals = new LinkedHashMap<>();
    for (Column c : columns) {
      if (!c.sum()) {
        continue;
      }
      if (c.kind() == Kind.INT) {
        totals.put(c.key(), rows.stream().mapToLong(r -> asLong(r.get(c.key()))).sum());
      } else {
        totals.put(
            c.key(),
            rows.stream()
                .map(r -> asMoney(r.get(c.key())))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
      }
    }
    totals.put(columns.get(0).key(), "Total");
    return totals;
  }

  static long asLong(Object v) {
    return v instanceof Number n ? n.longValue() : 0;
  }

  static BigDecimal asMoney(Object v) {
    return v instanceof BigDecimal b ? b : BigDecimal.ZERO.setScale(2);
  }
}
