package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A customer, known by phone number (stored as 2547XXXXXXXX). Promotional SMS only go to customers
 * who agreed (Kenya Data Protection Act); receipts need no consent.
 */
@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String phone;
  private String email;
  private String notes;
  private boolean marketingOptIn;
  private Instant optInAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected Customer() {}

  public Customer(String name, String phone, Instant now) {
    this.name = name;
    this.phone = phone;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String name, String phone, String email, String notes, Instant now) {
    this.name = name;
    this.phone = phone;
    this.email = email;
    this.notes = notes;
    this.updatedAt = now;
  }

  /** Records consent (or its withdrawal) and when it was given. */
  public void setMarketingOptIn(boolean optIn, Instant now) {
    if (optIn && !marketingOptIn) {
      optInAt = now;
    }
    if (!optIn) {
      optInAt = null;
    }
    marketingOptIn = optIn;
    updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public String getNotes() {
    return notes;
  }

  public boolean isMarketingOptIn() {
    return marketingOptIn;
  }
}
