package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A linked account (one per network). Tokens are stored encrypted - see TokenCipher. */
@Entity
@Table(name = "social_accounts")
public class SocialAccount {

  @Id
  @Enumerated(EnumType.STRING)
  private SocialNetwork network;

  private String externalId;
  private String name;
  private String accessToken;
  private String refreshToken;
  private Instant tokenExpiresAt;
  private Long linkedBy;
  private Instant linkedAt;

  protected SocialAccount() {}

  public SocialAccount(SocialNetwork network) {
    this.network = network;
  }

  /** Links (or re-links) the account; tokens must already be encrypted. */
  public void link(
      String externalId,
      String name,
      String encryptedAccessToken,
      String encryptedRefreshToken,
      Instant tokenExpiresAt,
      long userId,
      Instant now) {
    this.externalId = externalId;
    this.name = name;
    this.accessToken = encryptedAccessToken;
    this.refreshToken = encryptedRefreshToken;
    this.tokenExpiresAt = tokenExpiresAt;
    this.linkedBy = userId;
    this.linkedAt = now;
  }

  public void refreshed(
      String encryptedAccessToken, String encryptedRefreshToken, Instant expires) {
    this.accessToken = encryptedAccessToken;
    this.refreshToken = encryptedRefreshToken;
    this.tokenExpiresAt = expires;
  }

  public SocialNetwork getNetwork() {
    return network;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getName() {
    return name;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public Instant getTokenExpiresAt() {
    return tokenExpiresAt;
  }

  public Instant getLinkedAt() {
    return linkedAt;
  }
}
