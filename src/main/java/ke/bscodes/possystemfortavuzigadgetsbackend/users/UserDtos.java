package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;

/** Request and response bodies for /api/users. */
public final class UserDtos {

  private UserDtos() {}

  static final String PASSWORD_RULE = "Use at least 8 characters.";

  public record UserResponse(
      long id,
      String username,
      String fullName,
      Role role,
      boolean active,
      boolean locked,
      Instant createdAt) {

    static UserResponse from(User user, Instant now) {
      return new UserResponse(
          user.getId(),
          user.getUsername(),
          user.getFullName(),
          user.getRole(),
          user.isActive(),
          user.isLockedAt(now),
          user.getCreatedAt());
    }
  }

  public record CreateUserRequest(
      @NotBlank(message = "Enter a username.")
          @Pattern(
              regexp = "^[A-Za-z0-9._-]{3,50}$",
              message = "3-50 letters, digits, dots, dashes or underscores.")
          String username,
      @NotBlank(message = "Enter the full name.")
          @Size(max = 100, message = "At most 100 characters.")
          String fullName,
      @NotNull(message = "Choose a role.") Role role,
      @NotNull(message = "Enter a password.") @Size(min = 8, max = 100, message = PASSWORD_RULE)
          String password) {}

  public record UpdateUserRequest(
      @NotBlank(message = "Enter the full name.")
          @Size(max = 100, message = "At most 100 characters.")
          String fullName,
      @NotNull(message = "Choose a role.") Role role,
      boolean active) {}

  public record ResetPasswordRequest(
      @NotNull(message = "Enter a password.") @Size(min = 8, max = 100, message = PASSWORD_RULE)
          String password) {}
}
