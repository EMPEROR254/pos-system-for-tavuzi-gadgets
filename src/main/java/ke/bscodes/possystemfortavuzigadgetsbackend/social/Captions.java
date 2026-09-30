package ke.bscodes.possystemfortavuzigadgetsbackend.social;

/** The text that goes with a post on each network. */
public final class Captions {

  /** TikTok cuts photo titles at 90 characters. */
  static final int TIKTOK_TITLE_MAX = 90;

  private Captions() {}

  /** Title, then the description, then (if chosen) a link to chat with the shop on WhatsApp. */
  public static String caption(String title, String description, String whatsappNumber) {
    StringBuilder text = new StringBuilder(title.trim());
    if (description != null && !description.isBlank()) {
      text.append("\n\n").append(description.trim());
    }
    if (whatsappNumber != null) {
      text.append("\n\nOrder on WhatsApp: https://wa.me/").append(whatsappNumber);
    }
    return text.toString();
  }

  public static String tiktokTitle(String title) {
    String t = title.trim();
    return t.length() <= TIKTOK_TITLE_MAX ? t : t.substring(0, TIKTOK_TITLE_MAX - 1) + "…";
  }
}
