package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Permission;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.RolePermissions;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.User;

/** Request and response bodies for /api/auth. */
public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank(message = "Enter your username.") String username,
      @NotBlank(message = "Enter your password.") String password) {}

  public record ChangePasswordRequest(
      @NotBlank(message = "Enter your current password.") String currentPassword,
      @NotNull(message = "Enter a new password.")
          @Size(min = 8, max = 100, message = "Use at least 8 characters.")
          String newPassword) {}

  /** Who is signed in and what they may do - the web app shows or hides screens with this. */
  public record MeResponse(
      long id, String username, String fullName, Role role, Set<Permission> permissions) {

    public static MeResponse from(User user) {
      return new MeResponse(
          user.getId(),
          user.getUsername(),
          user.getFullName(),
          user.getRole(),
          RolePermissions.of(user.getRole()));
    }
  }

  /** Body of login and refresh. The refresh token itself travels in an httpOnly cookie. */
  public record TokenResponse(String accessToken, long expiresInSeconds, MeResponse user) {}

  /** What AuthService hands the controller: the body plus the new refresh token for the cookie. */
  public record Session(TokenResponse body, String refreshToken) {}
}
