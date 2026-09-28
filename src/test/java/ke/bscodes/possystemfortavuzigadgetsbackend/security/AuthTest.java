package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthTest {

  private static final String SECRET = "a-very-long-test-secret-of-32-chars!";
  private final Instant now = Instant.parse("2026-09-27T08:00:00Z");

  @Test
  void passwordsVerifyOnlyTheRightPassword() {
    String stored = PasswordHasher.hash("nakuru2026");
    assertThat(PasswordHasher.verify("nakuru2026", stored)).isTrue();
    assertThat(PasswordHasher.verify("wrong", stored)).isFalse();
    assertThat(PasswordHasher.hash("same")).isNotEqualTo(PasswordHasher.hash("same"));
  }

  @Test
  void rolesLimitWhatUsersMayDo() {
    assertThat(RolePermissions.can(Role.OWNER, Permission.MANAGE_USERS)).isTrue();
    assertThat(RolePermissions.can(Role.CASHIER, Permission.REFUND)).isFalse();
    assertThatThrownBy(() -> RolePermissions.require(Role.CASHIER, Permission.VIEW_REPORTS))
        .isInstanceOf(RolePermissions.ForbiddenException.class);
  }

  @Test
  void tokensRoundTripAndRejectTampering() {
    JwtTokens tokens = new JwtTokens(SECRET);
    String token = tokens.issue("user-1", Role.MANAGER, Duration.ofMinutes(15), now);
    JwtTokens.Claims claims = tokens.verify(token, now);
    assertThat(claims.subject()).isEqualTo("user-1");
    assertThat(claims.role()).isEqualTo(Role.MANAGER);

    assertThatThrownBy(() -> tokens.verify(token, now.plusSeconds(901)))
        .hasMessageContaining("expired");
    assertThatThrownBy(() -> new JwtTokens("another-secret-that-is-32-chars-long").verify(token, now))
        .hasMessageContaining("bad signature");
    assertThatThrownBy(() -> new JwtTokens("short")).isInstanceOf(IllegalArgumentException.class);
  }
}
