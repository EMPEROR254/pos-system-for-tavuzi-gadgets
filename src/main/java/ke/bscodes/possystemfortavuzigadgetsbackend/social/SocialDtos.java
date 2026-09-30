package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/** Request and response shapes for /api/social. */
public final class SocialDtos {

  private SocialDtos() {}

  /**
   * One network on the Social screen. canLink is false until the shop's app keys, public address
   * and token key are set up (see docs/SOCIAL_MEDIA.md); "setup" says what is missing.
   */
  public record AccountView(
      SocialNetwork network,
      boolean linked,
      String name,
      Instant linkedAt,
      boolean canLink,
      String setup) {}

  public record LinkStart(String authorizeUrl) {}

  public record WhatsAppRequest(
      @NotBlank(message = "Type the WhatsApp Business number.") String number) {}

  /** A post without its picture (lists never load pictures). */
  public record PostSummary(
      long id,
      String title,
      String description,
      String caption,
      String imageToken,
      String createdByName,
      Instant createdAt) {}

  public record TargetView(
      SocialNetwork network, SocialPostTarget.Status status, String error, int attempts) {}

  public record PostView(
      long id,
      String title,
      String description,
      String caption,
      String imageUrl,
      String createdByName,
      Instant createdAt,
      List<TargetView> targets) {}
}
