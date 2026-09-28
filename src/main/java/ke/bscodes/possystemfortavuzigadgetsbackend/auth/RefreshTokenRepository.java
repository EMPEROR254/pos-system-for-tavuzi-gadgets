package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Stored refresh tokens (by hash). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Signs a user out everywhere (password reset, disabled account, stolen token). Also expires the
   * tokens, so the short grace period for rotated tokens does not apply.
   */
  @Modifying
  @Query(
      """
      update RefreshToken t set t.revokedAt = coalesce(t.revokedAt, :now), t.expiresAt = :now
      where t.userId = :userId and t.expiresAt > :now
      """)
  int revokeAllForUser(long userId, Instant now);
}
