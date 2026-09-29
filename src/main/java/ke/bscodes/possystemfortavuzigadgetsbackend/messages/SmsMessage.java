package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One SMS in the outbox: queued, then sent (or failed after a few tries). */
@Entity
@Table(name = "sms_messages")
public class SmsMessage {

  public enum Kind {
    RECEIPT,
    PROMO,
    TEST
  }

  public enum Status {
    QUEUED,
    SENT,
    FAILED
  }

  static final int MAX_ATTEMPTS = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String phone;
  private String body;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  private Status status = Status.QUEUED;

  private int attempts;
  private String providerMessageId;
  private String error;
  private Long customerId;
  private Long saleId;
  private Long campaignId;
  private Instant createdAt;
  private Instant sentAt;

  protected SmsMessage() {}

  public SmsMessage(
      String phone,
      String body,
      Kind kind,
      Long customerId,
      Long saleId,
      Long campaignId,
      Instant now) {
    this.phone = phone;
    this.body = body;
    this.kind = kind;
    this.customerId = customerId;
    this.saleId = saleId;
    this.campaignId = campaignId;
    this.createdAt = now;
  }

  /** Records a send attempt; a failure stays queued until MAX_ATTEMPTS, then FAILED. */
  public void recordAttempt(SmsSender.Result result, Instant now) {
    attempts++;
    if (result.sent()) {
      status = Status.SENT;
      providerMessageId = result.providerMessageId();
      error = null;
      sentAt = now;
    } else {
      error =
          result.error() == null
              ? "Failed"
              : result.error().substring(0, Math.min(200, result.error().length()));
      status = attempts >= MAX_ATTEMPTS ? Status.FAILED : Status.QUEUED;
    }
  }

  public Long getId() {
    return id;
  }

  public String getPhone() {
    return phone;
  }

  public String getBody() {
    return body;
  }

  public Status getStatus() {
    return status;
  }
}
