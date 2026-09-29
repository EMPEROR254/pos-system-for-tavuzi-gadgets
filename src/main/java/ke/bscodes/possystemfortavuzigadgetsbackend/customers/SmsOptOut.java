package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A phone number that asked not to get promotions (replied STOP, or opted out with the network).
 * Kept even when the number is not a customer, so it is never messaged again by mistake.
 */
@Entity
@Table(name = "sms_opt_outs")
public class SmsOptOut {

  @Id private String phone;

  private String source;
  private Instant createdAt;

  protected SmsOptOut() {}

  public SmsOptOut(String phone, String source, Instant now) {
    this.phone = phone;
    this.source = source;
    this.createdAt = now;
  }
}
