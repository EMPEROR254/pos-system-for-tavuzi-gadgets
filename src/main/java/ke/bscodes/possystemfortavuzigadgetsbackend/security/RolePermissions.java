package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What each role may do. Check on the server for every protected action (e.g. in the service, or
 * with @PreAuthorize) - hiding a button is not security. Shared module from BS Codes.
 */
public final class RolePermissions {

  private static final Map<Role, Set<Permission>> DEFAULTS = new EnumMap<>(Role.class);

  static {
    DEFAULTS.put(Role.OWNER, EnumSet.allOf(Permission.class));
    // Spec: "No exchanges unless supervised by me" (the owner) - so managers
    // get no REFUND; returns and exchanges stay with the owner.
    DEFAULTS.put(
        Role.MANAGER,
        EnumSet.of(
            Permission.MAKE_SALE,
            Permission.GIVE_DISCOUNT,
            Permission.VIEW_REPORTS,
            Permission.MANAGE_PRODUCTS,
            Permission.MANAGE_STOCK,
            Permission.MANAGE_CUSTOMERS,
            Permission.SEND_PROMOTIONS));
    // Spec: some staff are blocked from discounts, refunds and reports.
    DEFAULTS.put(Role.CASHIER, EnumSet.of(Permission.MAKE_SALE));
  }

  private RolePermissions() {}

  public static boolean can(Role role, Permission permission) {
    return DEFAULTS.getOrDefault(role, Set.of()).contains(permission);
  }

  /** Everything the role may do (used for Spring Security authorities and the web app). */
  public static Set<Permission> of(Role role) {
    Set<Permission> permissions = DEFAULTS.get(role);
    return permissions == null ? Set.of() : EnumSet.copyOf(permissions);
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
