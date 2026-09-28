package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

/**
 * Implement this (as a Spring bean) to record M-Pesa results, e.g. mark the sale as paid. Must be
 * idempotent: ignore a CheckoutRequestID you've already recorded. Keep it fast; do slow work
 * asynchronously.
 */
public interface MpesaPaymentHandler {

  void onStkResult(StkCallback callback);
}
