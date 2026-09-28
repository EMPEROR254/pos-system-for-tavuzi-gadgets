package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.PasswordHasher;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Permission;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.RolePermissions;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.User;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supervisor approval at the till: when the signed-in person may not do something (a cashier giving
 * a discount, anyone but the owner taking a return), a person who may types their username and
 * password on the same screen.
 *
 * <p>Wrong passwords count towards the normal login lock, so this can't be used to guess the
 * owner's password. It runs in its own transaction so that count is saved even when the sale itself
 * is refused.
 */
@Service
public class ApprovalService {

  /** Username and password typed by the approving supervisor. */
  public record Approval(String username, String password) {}

  private final UserRepository users;
  private final AuthProperties props;
  private final Clock clock;

  public ApprovalService(UserRepository users, AuthProperties props, Clock clock) {
    this.users = users;
    this.props = props;
    this.clock = clock;
  }

  /**
   * Returns who allowed the action: the signed-in user if their role allows it, otherwise the
   * approving supervisor. Throws 403 when neither can.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
  public long authorise(AuthUser actor, Permission permission, Approval approval, String what) {
    if (RolePermissions.can(actor.role(), permission)) {
      return actor.id();
    }
    if (approval == null || isBlank(approval.username()) || isBlank(approval.password())) {
      throw new ApiException(HttpStatus.FORBIDDEN, what + " needs approval from a supervisor.");
    }
    Instant now = clock.instant();
    User supervisor =
        users
            .findByUsername(UserService.normaliseUsername(approval.username()))
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Wrong supervisor login."));
    if (supervisor.isLockedAt(now)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "That supervisor account is locked for now.");
    }
    if (!PasswordHasher.verify(approval.password(), supervisor.getPasswordHash())) {
      supervisor.recordFailedLogin(
          props.maxFailedLogins(), now.plus(Duration.ofMinutes(props.lockMinutes())));
      throw new ApiException(HttpStatus.FORBIDDEN, "Wrong supervisor login.");
    }
    if (!supervisor.isActive() || !RolePermissions.can(supervisor.getRole(), permission)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "That person may not approve this. " + what + " needs the right role.");
    }
    supervisor.recordSuccessfulLogin();
    return supervisor.getId();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
