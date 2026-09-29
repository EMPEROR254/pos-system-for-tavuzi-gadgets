package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A reply a customer sent to the shop's SMS number. */
@Entity
@Table(name = "sms_inbound")
public class SmsInbound {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String providerId;
  private String phone;
  private String text;
  private boolean optedOut;
  private Instant receivedAt;

  protected SmsInbound() {}

  public SmsInbound(String providerId, String phone, String text, boolean optedOut, Instant now) {
    this.providerId = providerId;
    this.phone = phone;
    this.text = text;
    this.optedOut = optedOut;
    this.receivedAt = now;
  }
}
