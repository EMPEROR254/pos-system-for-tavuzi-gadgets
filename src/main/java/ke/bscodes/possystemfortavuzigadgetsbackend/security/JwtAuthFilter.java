package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.function.LongFunction;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <token>" and signs the user in for this request.
 *
 * <p>The user is re-loaded from the database on every request, so a disabled account or a changed
 * role takes effect at once instead of when the 15-minute token runs out. With a handful of staff
 * this primary-key lookup is cheap.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtTokens tokens;
  private final LongFunction<AuthUser> activeUserById;
  private final Clock clock;

  /** activeUserById returns null when the user is missing or disabled. */
  public JwtAuthFilter(JwtTokens tokens, LongFunction<AuthUser> activeUserById, Clock clock) {
    this.tokens = tokens;
    this.activeUserById = activeUserById;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        JwtTokens.Claims claims = tokens.verify(header.substring(7), clock.instant());
        AuthUser user = activeUserById.apply(Long.parseLong(claims.subject()));
        if (user != null) {
          var auth = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (JwtTokens.InvalidTokenException | NumberFormatException e) {
        // Not signed in: protected endpoints answer 401 and the app refreshes the token.
      }
    }
    chain.doFilter(request, response);
  }
}
