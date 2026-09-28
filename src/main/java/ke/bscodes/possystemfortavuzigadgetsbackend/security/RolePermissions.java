package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What each role may do. Check on the server for every protected action (e.g.
 * in the service, or with @PreAuthorize) - hiding a button is not security.
 * Shared module from BS Codes.
 */
public final class RolePermissions {

  private static final Map<Role, Set<Permission>> DEFAULTS = new EnumMap<>(Role.class);

  static {
    DEFAULTS.put(Role.OWNER, EnumSet.allOf(Permission.class));
    DEFAULTS.put(
        Role.MANAGER,
        EnumSet.of(
            Permission.MAKE_SALE,
            Permission.GIVE_DISCOUNT,
            Permission.REFUND,
            Permission.VIEW_REPORTS,
            Permission.MANAGE_PRODUCTS,
            Permission.MANAGE_STOCK));
    DEFAULTS.put(Role.CASHIER, EnumSet.of(Permission.MAKE_SALE));
    DEFAULTS.put(Role.STOCK_CLERK, EnumSet.of(Permission.MANAGE_STOCK));
    DEFAULTS.put(Role.ACCOUNTANT, EnumSet.of(Permission.VIEW_REPORTS));
  }

  private RolePermissions() {}

  public static boolean can(Role role, Permission permission) {
    return DEFAULTS.getOrDefault(role, Set.of()).contains(permission);
  }

  /** Throws ForbiddenException unless the role has the permission. */
  public static void require(Role role, Permission permission) {
    if (!can(role, permission)) {
      throw new ForbiddenException(permission);
    }
  }

  /** Thrown when a user tries something their role doesn't allow (map it to HTTP 403). */
  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(Permission permission) {
      super("Not allowed: " + permission);
    }
  }
}
