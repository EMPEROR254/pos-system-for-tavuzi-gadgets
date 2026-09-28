package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.MeResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.Session;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.TokenResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.JwtTokens;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.PasswordHasher;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.User;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in with username and password. Gives a short-lived access token (15 min, sent as a Bearer
 * header) and a refresh token (7 days, httpOnly cookie) that is rotated on every use. Too many
 * wrong passwords lock the account for a while.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  // Checked when the username doesn't exist, so the answer takes as long as a
  // real check and doesn't reveal which usernames exist.
  private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

  // Two tabs (or two quick requests) may refresh with the same token at once;
  // within this window that is normal, not a stolen token.
  private static final Duration REUSE_GRACE = Duration.ofSeconds(30);

  static final String WRONG_LOGIN = "Wrong username or password.";

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final JwtTokens jwt;
  private final AuthProperties props;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      JwtTokens jwt,
      AuthProperties props,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.jwt = jwt;
    this.props = props;
    this.clock = clock;
  }

  // noRollbackFor: the failed-login counter must be saved even though we answer with an error.
  @Transactional(noRollbackFor = ApiException.class)
  public Session login(String username, String password) {
    Instant now = clock.instant();
    User user = users.findByUsername(UserService.normaliseUsername(username)).orElse(null);
    if (user == null) {
      PasswordHasher.verify(password, DUMMY_HASH);
      throw new ApiException(HttpStatus.UNAUTHORIZED, WRONG_LOGIN);
    }
    if (user.isLockedAt(now)) {
      throw lockedError(user, now);
    }
    if (!PasswordHasher.verify(password, user.getPasswordHash())) {
      user.recordFailedLogin(
          props.maxFailedLogins(), now.plus(Duration.ofMinutes(props.lockMinutes())));
      if (user.isLockedAt(now)) {
        log.warn("Account '{}' locked after too many wrong passwords", user.getUsername());
        throw lockedError(user, now);
      }
      throw new ApiException(HttpStatus.UNAUTHORIZED, WRONG_LOGIN);
    }
    if (!user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "This account is disabled. Ask the owner.");
    }
    user.recordSuccessfulLogin();
    return newSession(user, now);
  }

  /** Swaps a valid refresh token for a new access token and a new refresh token. */
  @Transactional(noRollbackFor = ApiException.class)
  public Session refresh(String rawToken) {
    Instant now = clock.instant();
    RefreshToken stored = findToken(rawToken);
    if (stored.isExpiredAt(now)) {
      throw expiredSession();
    }
    if (stored.isRevoked() && !stored.wasRevokedWithin(REUSE_GRACE, now)) {
      // An old token was used again: it may have been copied. Sign that user out everywhere.
      refreshTokens.revokeAllForUser(stored.getUserId(), now);
      throw expiredSession();
    }
    User user =
        users.findById(stored.getUserId()).filter(User::isActive).orElseThrow(this::expiredSession);
    stored.revoke(now);
    return newSession(user, now);
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken != null && !rawToken.isBlank()) {
      refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> t.end(clock.instant()));
    }
  }

  @Transactional(readOnly = true)
  public MeResponse me(long userId) {
    return users.findById(userId).map(MeResponse::from).orElseThrow(this::expiredSession);
  }

  @Transactional
  public void changeOwnPassword(long userId, String currentPassword, String newPassword) {
    User user = users.findById(userId).orElseThrow(this::expiredSession);
    if (!PasswordHasher.verify(currentPassword, user.getPasswordHash())) {
      throw ApiException.badRequest("Your current password is wrong.");
    }
    user.changePassword(PasswordHasher.hash(newPassword), clock.instant());
  }

  private Session newSession(User user, Instant now) {
    Duration accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
    String accessToken = jwt.issue(String.valueOf(user.getId()), user.getRole(), accessTtl, now);
    String refreshToken = randomToken();
    refreshTokens.save(
        new RefreshToken(
            user.getId(),
            sha256(refreshToken),
            now.plus(Duration.ofDays(props.refreshTokenDays())),
            now));
    return new Session(
        new TokenResponse(accessToken, accessTtl.toSeconds(), MeResponse.from(user)), refreshToken);
  }

  private RefreshToken findToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw expiredSession();
    }
    return refreshTokens.findByTokenHash(sha256(rawToken)).orElseThrow(this::expiredSession);
  }

  private ApiException expiredSession() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again.");
  }

  private ApiException lockedError(User user, Instant now) {
    long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
    return new ApiException(
        HttpStatus.LOCKED,
        "Too many wrong passwords. Try again in " + minutes + " minutes or ask the owner.");
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
