package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

/**
 * Why someone changed stock by hand. WARRANTY_REPAIR covers spare parts and accessories used to
 * repair or replace an item under warranty (spec: track stock of warranty parts).
 */
public enum AdjustmentReason {
  OPENING_STOCK,
  DAMAGED,
  LOST_OR_STOLEN,
  WARRANTY_REPAIR,
  RETURNED_TO_SUPPLIER,
  CORRECTION
}
