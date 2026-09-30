package ke.bscodes.possystemfortavuzigadgetsbackend.social;

/**
 * The shop's social media. Facebook, Instagram and TikTok are posted to by the server; WhatsApp has
 * no API for posting (Status or Channels), so posts are shared to it from the phone instead.
 */
public enum SocialNetwork {
  FACEBOOK,
  INSTAGRAM,
  TIKTOK,
  WHATSAPP;

  public boolean postedByServer() {
    return this != WHATSAPP;
  }
}
