package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A one-time "state" value for linking an account (protects against a forged link request). */
@Entity
@Table(name = "social_oauth_states")
public class OAuthState {

  @Id private String state;
  private String provider;
  private Long userId;
  private Instant createdAt;

  protected OAuthState() {}

  public OAuthState(String state, String provider, long userId, Instant now) {
    this.state = state;
    this.provider = provider;
    this.userId = userId;
    this.createdAt = now;
  }

  public String getProvider() {
    return provider;
  }

  public Long getUserId() {
    return userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
