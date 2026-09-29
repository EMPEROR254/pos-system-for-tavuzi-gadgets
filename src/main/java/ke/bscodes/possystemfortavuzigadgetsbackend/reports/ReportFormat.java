package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Kind;

/**
 * How report values are written in PDF (for people) and CSV (for spreadsheets: plain numbers, ISO
 * dates). Times are shown in Nairobi.
 */
public final class ReportFormat {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter DAY_TIME =
      DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH).withZone(ReportRange.NAIROBI);
  private static final DateTimeFormatter CSV_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ReportRange.NAIROBI);

  private ReportFormat() {}

  public static String date(LocalDate d) {
    return DAY.format(d);
  }

  /** "1,250.00", "12", "34.5%", "28 Sep 2026", "28 Sep 2026 10:00". */
  public static String forPeople(Object value, Kind kind) {
    if (value == null) {
      return "";
    }
    return switch (kind) {
      case MONEY -> number("#,##0.00").format(value);
      case INT -> value instanceof Number ? number("#,##0").format(value) : value.toString();
      case PERCENT -> number("0.0").format(value) + "%";
      case DATE -> value instanceof LocalDate d ? date(d) : value.toString();
      case DATETIME -> value instanceof Instant i ? DAY_TIME.format(i) : value.toString();
      case TEXT -> value.toString();
    };
  }

  /** Plain values for spreadsheets: 1250.00, 2026-09-28, 2026-09-28 10:00. */
  public static String forCsv(Object value, Kind kind) {
    if (value == null) {
      return "";
    }
    return switch (kind) {
      case MONEY, PERCENT -> value instanceof BigDecimal b ? b.toPlainString() : value.toString();
      case DATETIME -> value instanceof Instant i ? CSV_TIME.format(i) : value.toString();
      default -> value.toString();
    };
  }

  // DecimalFormat is not thread-safe, so each call gets its own.
  private static DecimalFormat number(String pattern) {
    return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ENGLISH));
  }
}
