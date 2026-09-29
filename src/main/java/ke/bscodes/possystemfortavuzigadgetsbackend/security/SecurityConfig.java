package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security rules: the web app (static files and page URLs) is public, every /api/** endpoint needs
 * a valid access token except login/refresh/logout, the status check and the M-Pesa callback. Finer
 * rules use @PreAuthorize with the permission names, e.g.
 * {@code @PreAuthorize("hasAuthority('MANAGE_USERS')")}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  JwtTokens jwtTokens(AuthProperties properties) {
    return new JwtTokens(properties.jwtSecret());
  }

  /** Stops Spring Boot from creating its default in-memory user; we have our own login. */
  @Bean
  UserDetailsService noDefaultUsers() {
    return username -> {
      throw new UnsupportedOperationException("Login goes through /api/auth/login");
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtTokens tokens, UserRepository users, Clock clock, ObjectMapper json)
      throws Exception {
    JwtAuthFilter jwtFilter =
        new JwtAuthFilter(
            tokens,
            id ->
                users
                    .findById(id)
                    .filter(u -> u.isActive())
                    .map(u -> new AuthUser(u.getId(), u.getUsername(), u.getRole()))
                    .orElse(null),
            clock);

    return http
        // No cookies are used for API access (bearer tokens), so CSRF can't
        // trigger actions. The refresh cookie is SameSite=Strict.
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // A streamed download (CSV export) finishes in a second "async" dispatch that
                // carries no token; the request itself was already checked, so let it finish.
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/status",
                        "/api/mpesa/callback",
                        "/api/sms/callbacks/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // The web app, its page URLs, /actuator/health and (dev only) Swagger.
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            writeProblem(res, json, HttpStatus.UNAUTHORIZED, "Please sign in."))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            writeProblem(
                                res, json, HttpStatus.FORBIDDEN, "Your role may not do this.")))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void writeProblem(
      HttpServletResponse response, ObjectMapper json, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
  }
}
