package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;

/**
 * A staff account. Accounts are never deleted (sales will point at them); they are disabled
 * instead.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  private boolean active = true;
  private int failedLogins;
  private Instant lockedUntil;
  private Instant createdAt;
  private Instant updatedAt;

  protected User() {}

  public User(String username, String fullName, String passwordHash, Role role, Instant now) {
    this.username = username;
    this.fullName = fullName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /** Counts a wrong password; after maxFailures the account is locked for a while. */
  public void recordFailedLogin(int maxFailures, Instant lockUntil) {
    failedLogins++;
    if (failedLogins >= maxFailures) {
      failedLogins = 0;
      lockedUntil = lockUntil;
    }
  }

  public void recordSuccessfulLogin() {
    failedLogins = 0;
    lockedUntil = null;
  }

  public void changePassword(String newHash, Instant now) {
    this.passwordHash = newHash;
    recordSuccessfulLogin();
    this.updatedAt = now;
  }

  public void update(String fullName, Role role, boolean active, Instant now) {
    this.fullName = fullName;
    this.role = role;
    this.active = active;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public int getFailedLogins() {
    return failedLogins;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
