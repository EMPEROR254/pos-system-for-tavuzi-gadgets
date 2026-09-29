package ke.bscodes.possystemfortavuzigadgetsbackend.security;

/** Things a user may be allowed to do. */
public enum Permission {
  MAKE_SALE,
  GIVE_DISCOUNT,
  REFUND,
  VIEW_REPORTS,
  MANAGE_PRODUCTS,
  MANAGE_STOCK,
  MANAGE_USERS,
  MANAGE_SETTINGS,
  /** See and edit the full customer list (the till can still find and add customers). */
  MANAGE_CUSTOMERS,
  /** Send promotional SMS to customers who agreed to receive them. */
  SEND_PROMOTIONS
}
