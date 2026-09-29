package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

/**
 * SMS length rules: plain text (GSM-7) fits 160 characters in one SMS and 153 per part when longer;
 * text with other characters (e.g. emoji) fits 70, then 67 per part. Each part is billed.
 */
public final class SmsText {

  private static final String GSM =
      "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
          + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
  private static final String GSM_EXTENDED = "^{}\\[~]|€";

  private SmsText() {}

  public static int parts(String text) {
    boolean gsm = true;
    int length = 0;
    for (char c : text.toCharArray()) {
      if (GSM.indexOf(c) >= 0) {
        length++;
      } else if (GSM_EXTENDED.indexOf(c) >= 0) {
        length += 2;
      } else {
        gsm = false;
      }
    }
    if (!gsm) {
      length = text.length();
      return length <= 70 ? 1 : (int) Math.ceil(length / 67.0);
    }
    return length <= 160 ? 1 : (int) Math.ceil(length / 153.0);
  }
}
