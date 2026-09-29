package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A promotional SMS sent to a group of customers who agreed to receive them. */
@Entity
@Table(name = "sms_campaigns")
public class SmsCampaign {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String message;
  private String audience;
  private int recipients;
  private Long createdBy;
  private Instant createdAt;

  protected SmsCampaign() {}

  public SmsCampaign(String message, String audience, int recipients, long createdBy, Instant now) {
    this.message = message;
    this.audience = audience;
    this.recipients = recipients;
    this.createdBy = createdBy;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }
}
