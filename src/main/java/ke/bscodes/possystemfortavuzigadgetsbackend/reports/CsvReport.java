package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;

/** Writes a report as CSV for Excel or Google Sheets, with a totals line at the end. */
public final class CsvReport {

  private CsvReport() {}

  public static void write(ReportTable table, Writer out) throws IOException {
    List<Column> columns = table.columns();
    out.write(line(columns.stream().map(Column::label).toList()));
    for (Map<String, Object> row : table.rows()) {
      out.write(line(values(columns, row)));
    }
    if (table.totals() != null) {
      out.write(line(values(columns, table.totals())));
    }
    out.flush();
  }

  private static List<String> values(List<Column> columns, Map<String, Object> row) {
    List<String> values = new ArrayList<>();
    for (Column c : columns) {
      String v = ReportFormat.forCsv(row.get(c.key()), c.kind());
      values.add(c.kind() == Kind.TEXT ? defuse(v) : v);
    }
    return values;
  }

  /**
   * A product name starting with "=", "+", "-" or "@" would run as a formula when the file is
   * opened in a spreadsheet, so text cells like that get a leading apostrophe.
   */
  static String defuse(String v) {
    return !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
  }

  static String line(List<String> values) {
    return String.join(",", values.stream().map(CsvReport::quote).toList()) + "\r\n";
  }

  static String quote(String v) {
    return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
        ? "\"" + v.replace("\"", "\"\"") + "\""
        : v;
  }
}
