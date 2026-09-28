package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * A long-lived (7 day) sign-in on one device. Only the SHA-256 hash of the token is stored, so a
 * database leak does not leak working tokens.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant revokedAt;
  private Instant createdAt;

  protected RefreshToken() {}

  public RefreshToken(long userId, String tokenHash, Instant expiresAt, Instant now) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean wasRevokedWithin(Duration window, Instant now) {
    return revokedAt != null && revokedAt.plus(window).isAfter(now);
  }

  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  /** Replaced by a newer token (rotation); a short grace period still applies. */
  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  /** Ended for good (logout): no grace period. */
  public void end(Instant now) {
    revoke(now);
    expiresAt = now;
  }

  public Long getUserId() {
    return userId;
  }
}
