package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/** Daraja's timestamp and password rules. */
public final class DarajaPassword {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));

  private DarajaPassword() {}

  /** yyyyMMddHHmmss in Nairobi time. */
  public static String timestamp(Instant now) {
    return TIMESTAMP.format(now);
  }

  /** base64(shortcode + passkey + timestamp). */
  public static String password(String shortcode, String passkey, String timestamp) {
    String raw = shortcode + passkey + timestamp;
    return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
