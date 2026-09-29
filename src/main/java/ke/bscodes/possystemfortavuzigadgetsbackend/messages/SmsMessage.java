package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;

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

  /** Not before this time (scheduled promotions, quiet hours); null = straight away. */
  private Instant sendAfter;

  /** What the network reported (e.g. Success, Failed, Rejected); null until it reports. */
  private String deliveryStatus;

  private String failureReason;
  private Instant deliveredAt;

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

  public void setSendAfter(Instant sendAfter) {
    this.sendAfter = sendAfter;
  }

  /** The network's delivery report. "Success" means it reached the phone. */
  public void recordDelivery(String status, String reason, Instant now) {
    this.deliveryStatus = cut(status, 30);
    this.failureReason = reason == null || reason.isBlank() ? null : cut(reason, 100);
    if ("Success".equalsIgnoreCase(status)) {
      this.deliveredAt = now;
    }
  }

  /** Final delivery reports that mean the SMS never reached the phone. */
  static final Set<String> UNDELIVERED =
      Set.of("Failed", "Rejected", "Expired", "AbsentSubscriber");

  /** Failed to send, or sent but the network could not deliver it. */
  public boolean canResend() {
    return status == Status.FAILED
        || (status == Status.SENT && UNDELIVERED.contains(deliveryStatus));
  }

  private static String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  /** Try a failed message again from the start. */
  public void resend() {
    status = Status.QUEUED;
    attempts = 0;
    error = null;
    deliveryStatus = null;
    failureReason = null;
    sendAfter = null;
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

  public Kind getKind() {
    return kind;
  }

  public String getProviderMessageId() {
    return providerMessageId;
  }

  public String getDeliveryStatus() {
    return deliveryStatus;
  }

  public Status getStatus() {
    return status;
  }
}
