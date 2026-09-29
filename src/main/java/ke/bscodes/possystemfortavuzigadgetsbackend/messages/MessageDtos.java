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
      Instant sentAt,
      Instant sendAfter,
      String deliveryStatus,
      String failureReason) {}

  /**
   * A promotion. Goes only to customers who agreed to promotions, optionally only those who bought
   * in the last boughtWithinDays days and/or bought something in category.
   */
  public record CampaignRequest(
      @NotBlank(message = "Write the message.")
          @Size(max = 300, message = "Keep it under 300 characters (2 SMS).")
          String message,
      @Min(value = 1, message = "At least 1 day.") @Max(value = 3650) Integer boughtWithinDays,
      @Size(max = 60) String category,
      Instant sendAt) {}

  /** How many would receive it and how many SMS each costs (with the opt-out line added). */
  public record CampaignPreview(int recipients, String fullMessage, int smsParts) {}

  /** sendsAt: when it will actually start going out (after any quiet hours). */
  public record CampaignResult(long campaignId, int recipients, int smsParts, Instant sendsAt) {}

  public record CampaignView(
      long id,
      String message,
      String audience,
      Instant createdAt,
      Instant sendAt,
      int recipients,
      long sent,
      long delivered,
      long failed,
      long waiting) {}

  public record ReplyView(
      long id, String phone, String text, boolean optedOut, Instant receivedAt) {}

  /** SMS account status for the Messages screen. */
  public record SmsAccount(boolean live, String balance, String quietHours) {}
}
