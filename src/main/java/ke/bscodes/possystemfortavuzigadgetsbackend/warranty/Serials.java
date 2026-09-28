package ke.bscodes.possystemfortavuzigadgetsbackend.warranty;

import java.util.Locale;
import java.util.regex.Pattern;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;

/**
 * Checks IMEIs and serial numbers typed or scanned at the till. An IMEI is 15 digits whose last
 * digit is a Luhn check digit, which catches most typing mistakes.
 */
public final class Serials {

  private static final Pattern SERIAL = Pattern.compile("^[A-Z0-9][A-Z0-9/\\-]{2,39}$");
  private static final Pattern IMEI = Pattern.compile("^\\d{15}$");

  private Serials() {}

  /** Upper-case and without spaces, the way it is stored. */
  public static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
  }

  /** null when fine, otherwise a message for the cashier. */
  public static String problem(SerialType type, String normalised) {
    if (type == SerialType.IMEI) {
      if (!IMEI.matcher(normalised).matches()) {
        return "An IMEI has 15 digits (dial *#06# on the phone).";
      }
      return luhnValid(normalised)
          ? null
          : "IMEI " + normalised + " is not valid - check for a typo.";
    }
    return SERIAL.matcher(normalised).matches()
        ? null
        : "Serial numbers are 3-40 letters or digits.";
  }

  static boolean luhnValid(String digits) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      int d = digits.charAt(digits.length() - 1 - i) - '0';
      if (i % 2 == 1) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
    }
    return sum % 10 == 0;
  }
}
