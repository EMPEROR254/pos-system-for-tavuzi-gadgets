package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;

/**
 * The days a report covers, as the shop sees them (Nairobi), and the matching UTC instants for the
 * database: [start of "from", start of the day after "to").
 */
public record ReportRange(LocalDate from, LocalDate to) {

  public static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
  static final int MAX_DAYS = 366;

  /** From the query string; both default to today in Nairobi. */
  public static ReportRange of(LocalDate from, LocalDate to, Clock clock) {
    LocalDate today = LocalDate.now(clock.withZone(NAIROBI));
    LocalDate start = from == null ? today : from;
    LocalDate end = to == null ? (from == null ? today : start) : to;
    if (end.isBefore(start)) {
      throw ApiException.badRequest("The end date is before the start date.");
    }
    if (start.plusDays(MAX_DAYS).isBefore(end)) {
      throw ApiException.badRequest("Choose a date range of up to one year.");
    }
    return new ReportRange(start, end);
  }

  public Instant start() {
    return from.atStartOfDay(NAIROBI).toInstant();
  }

  public Instant end() {
    return to.plusDays(1).atStartOfDay(NAIROBI).toInstant();
  }

  public long days() {
    return to.toEpochDay() - from.toEpochDay() + 1;
  }
}
