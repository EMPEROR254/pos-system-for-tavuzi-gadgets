package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "app.social" (see application.yml and .env.example). */
@ConfigurationProperties(prefix = "app.social")
public record SocialProperties(
    String publicUrl,
    String tokenKey,
    String metaAppId,
    String metaAppSecret,
    String metaGraphUrl,
    String metaDialogUrl,
    String tiktokClientKey,
    String tiktokClientSecret,
    String tiktokApiUrl,
    String tiktokAuthUrl,
    boolean dispatchEnabled) {

  public SocialProperties {
    publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
  }

  public boolean hasPublicUrl() {
    return !publicUrl.isBlank();
  }

  public boolean metaConfigured() {
    return filled(metaAppId) && filled(metaAppSecret);
  }

  public boolean tiktokConfigured() {
    return filled(tiktokClientKey) && filled(tiktokClientSecret);
  }

  /** Where Facebook/TikTok send the owner back after they agree to link the account. */
  public String redirectUri(String provider) {
    return publicUrl + "/api/social/callback/" + provider;
  }

  /** The public link Instagram and TikTok fetch a post's picture from. */
  public String imageUrl(String token) {
    return publicUrl + "/api/social/images/" + token + ".jpg";
  }

  private static boolean filled(String s) {
    return s != null && !s.isBlank();
  }
}
