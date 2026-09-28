package ke.bscodes.possystemfortavuzigadgetsbackend.security;

/**
 * Staff roles from the spec: Owner, Manager and Cashier / front desk. Change together with
 * RolePermissions and the role check in db/migration/V1__init.sql.
 */
public enum Role {
  OWNER,
  MANAGER,
  CASHIER
}
