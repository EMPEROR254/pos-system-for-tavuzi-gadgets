package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

/** Kenyan mobile numbers in the form Daraja expects: 2547XXXXXXXX or 2541XXXXXXXX. */
public final class Phones {

  private Phones() {}

  /** "0712 345 678", "+254712345678", "712345678" → "254712345678". */
  public static String normalize(String input) {
    String digits = input == null ? "" : input.replaceAll("\\D", "");
    String phone = digits;
    if (digits.startsWith("0") && digits.length() == 10) {
      phone = "254" + digits.substring(1);
    } else if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) {
      phone = "254" + digits;
    }
    if (!phone.matches("254[17]\\d{8}")) {
      throw new IllegalArgumentException("Not a valid Kenyan mobile number: " + input);
    }
    return phone;
  }
}
