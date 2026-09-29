package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Someone the shop buys stock from. */
@Entity
@Table(name = "suppliers")
public class Supplier {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String phone;
  private String email;
  private String notes;
  private boolean active = true;
  private Instant createdAt;

  protected Supplier() {}

  public Supplier(Instant now) {
    this.createdAt = now;
  }

  public void update(String name, String phone, String email, String notes, boolean active) {
    this.name = name;
    this.phone = phone;
    this.email = email;
    this.notes = notes;
    this.active = active;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
