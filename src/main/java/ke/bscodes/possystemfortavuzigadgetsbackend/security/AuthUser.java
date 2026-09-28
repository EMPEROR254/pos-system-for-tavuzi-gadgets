package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The signed-in staff member for the current request. Get it in a controller with
 * {@code @AuthenticationPrincipal AuthUser me}.
 */
public record AuthUser(long id, String username, Role role) {

  /** "ROLE_OWNER" plus one authority per permission, e.g. "VIEW_REPORTS". */
  public List<GrantedAuthority> authorities() {
    return Stream.concat(
            Stream.of("ROLE_" + role.name()),
            RolePermissions.of(role).stream().map(Permission::name))
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .toList();
  }
}
