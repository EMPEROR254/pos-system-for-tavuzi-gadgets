package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * No promotional SMS at night (Kenyan time). A promotion due inside the quiet hours is held until
 * they end. Receipts are not affected - the customer is standing at the counter. The hours come
 * from configuration (default 20:00 to 08:00).
 */
public record QuietHours(LocalTime start, LocalTime end) {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

  public boolean contains(Instant when) {
    LocalTime t = when.atZone(NAIROBI).toLocalTime();
    return start.isAfter(end)
        ? !t.isBefore(start) || t.isBefore(end) // e.g. 20:00-08:00, across midnight
        : !t.isBefore(start) && t.isBefore(end);
  }

  /** when itself, or the end of the quiet hours it falls in. */
  public Instant nextAllowed(Instant when) {
    if (!contains(when)) {
      return when;
    }
    ZonedDateTime local = when.atZone(NAIROBI);
    ZonedDateTime endToday = local.with(end);
    return (endToday.isAfter(local) ? endToday : endToday.plusDays(1)).toInstant();
  }
}
