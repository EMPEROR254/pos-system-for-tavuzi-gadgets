package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

/** A sale stays COMPLETED unless items were brought back (owner-approved returns). */
public enum SaleStatus {
  COMPLETED,
  PARTLY_RETURNED,
  RETURNED
}
