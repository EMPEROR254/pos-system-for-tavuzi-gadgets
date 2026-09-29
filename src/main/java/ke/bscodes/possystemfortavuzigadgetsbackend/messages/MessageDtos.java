package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response bodies for /api/messages. */
public final class MessageDtos {

  private MessageDtos() {}

  public record MessageView(
      long id,
      String phone,
      String body,
      String kind,
      String status,
      int attempts,
      String error,
      Instant createdAt,
      Instant sentAt) {}

  /**
   * A promotion. Goes only to customers who agreed to promotions, optionally only those who bought
   * in the last boughtWithinDays days and/or bought something in category.
   */
  public record CampaignRequest(
      @NotBlank(message = "Write the message.")
          @Size(max = 300, message = "Keep it under 300 characters (2 SMS).")
          String message,
      @Min(value = 1, message = "At least 1 day.") @Max(value = 3650) Integer boughtWithinDays,
      @Size(max = 60) String category) {}

  /** How many would receive it and how many SMS each costs (with the opt-out line added). */
  public record CampaignPreview(int recipients, String fullMessage, int smsParts) {}

  public record CampaignResult(long campaignId, int recipients, int smsParts) {}
}
