// Roles and permissions, mirroring the server (security/Role.java and
// RolePermissions.java). The web app only uses these to show or hide
// screens - the server checks every request itself.
export type Role = 'OWNER' | 'MANAGER' | 'CASHIER'

export type Permission =
  | 'MAKE_SALE'
  | 'GIVE_DISCOUNT'
  | 'REFUND'
  | 'VIEW_REPORTS'
  | 'MANAGE_PRODUCTS'
  | 'MANAGE_STOCK'
  | 'MANAGE_USERS'
  | 'MANAGE_SETTINGS'

export const ROLES: { value: Role; label: string; description: string }[] = [
  { value: 'OWNER', label: 'Owner', description: 'Everything, including staff accounts and returns' },
  { value: 'MANAGER', label: 'Manager', description: 'Sales, discounts, stock, products and reports' },
  { value: 'CASHIER', label: 'Cashier / front desk', description: 'Sales only - no discounts, refunds or reports' },
]

export function roleLabel(role: Role): string {
  return ROLES.find((r) => r.value === role)?.label ?? role
}

export function can(user: { permissions: Permission[] } | null, permission: Permission): boolean {
  return user?.permissions.includes(permission) ?? false
}
