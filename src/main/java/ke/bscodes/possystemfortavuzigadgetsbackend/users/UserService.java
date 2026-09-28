package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.RefreshTokenRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.PasswordHasher;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.CreateUserRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.UpdateUserRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing staff accounts. Rules that keep the shop from locking itself out: you can't disable
 * yourself or change your own role, and there is always at least one active owner.
 */
@Service
public class UserService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final Clock clock;

  public UserService(UserRepository users, RefreshTokenRepository refreshTokens, Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
  }

  /** Usernames are case-insensitive: "Jane" and "jane" are the same account. */
  public static String normaliseUsername(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional(readOnly = true)
  public Page<UserResponse> search(String query, Role role, Boolean active, Pageable pageable) {
    String pattern =
        query == null || query.isBlank()
            ? null
            : "%"
                + query.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "\\_")
                + "%";
    Instant now = clock.instant();
    return users.search(pattern, role, active, pageable).map(u -> UserResponse.from(u, now));
  }

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    String username = normaliseUsername(request.username());
    if (users.existsByUsername(username)) {
      throw ApiException.conflict("That username is already taken.");
    }
    Instant now = clock.instant();
    User user =
        new User(
            username,
            request.fullName().trim(),
            PasswordHasher.hash(request.password()),
            request.role(),
            now);
    return UserResponse.from(users.save(user), now);
  }

  @Transactional
  public UserResponse update(AuthUser actor, long id, UpdateUserRequest request) {
    User user = find(id);
    if (user.getId() == actor.id()) {
      if (!request.active()) {
        throw ApiException.badRequest("You can't disable your own account.");
      }
      if (request.role() != user.getRole()) {
        throw ApiException.badRequest("You can't change your own role.");
      }
    }
    boolean losesOwner =
        user.getRole() == Role.OWNER
            && user.isActive()
            && (request.role() != Role.OWNER || !request.active());
    if (losesOwner && users.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
      throw ApiException.badRequest("There must always be at least one active owner.");
    }
    Instant now = clock.instant();
    user.update(request.fullName().trim(), request.role(), request.active(), now);
    if (!request.active()) {
      refreshTokens.revokeAllForUser(user.getId(), now);
    }
    return UserResponse.from(user, now);
  }

  /** Owner sets a new password for a staff member (also unlocks the account). */
  @Transactional
  public void resetPassword(long id, String newPassword) {
    User user = find(id);
    Instant now = clock.instant();
    user.changePassword(PasswordHasher.hash(newPassword), now);
    // Signs the person out on every other device.
    refreshTokens.revokeAllForUser(user.getId(), now);
  }

  private User find(long id) {
    return users.findById(id).orElseThrow(() -> ApiException.notFound("No such user."));
  }
}
