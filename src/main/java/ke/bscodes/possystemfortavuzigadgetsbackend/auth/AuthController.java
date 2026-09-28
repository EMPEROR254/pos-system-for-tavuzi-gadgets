package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import jakarta.validation.Valid;
import java.time.Duration;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.ChangePasswordRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.LoginRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.MeResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.Session;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.AuthDtos.TokenResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in and out: /api/auth. The refresh token is set as an httpOnly cookie (JavaScript can't read
 * it) limited to /api/auth; the access token is returned in the body and kept in memory by the web
 * app.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  static final String REFRESH_COOKIE = "refresh_token";

  private final AuthService service;
  private final AuthProperties props;

  public AuthController(AuthService service, AuthProperties props) {
    this.service = service;
    this.props = props;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return withCookie(service.login(request.username(), request.password()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    return withCookie(service.refresh(refreshToken));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    service.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthUser me) {
    return service.me(me.id());
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest request) {
    service.changeOwnPassword(me.id(), request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<TokenResponse> withCookie(Session session) {
    ResponseCookie cookie =
        cookie(session.refreshToken(), Duration.ofDays(props.refreshTokenDays()));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(session.body());
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(props.cookieSecure())
        // Strict: other websites can't make the browser send it (no CSRF on refresh).
        .sameSite("Strict")
        .path("/api/auth")
        .maxAge(maxAge)
        .build();
  }
}
