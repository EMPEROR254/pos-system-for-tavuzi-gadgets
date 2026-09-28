package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

/**
 * How a customer paid. M-Pesa codes are typed by the cashier from the customer's SMS; the card
 * machine is not connected, so the cashier types its approval code (optional).
 */
public enum PaymentMethod {
  CASH("Cash"),
  MPESA_TILL("M-Pesa Till"),
  MPESA_PAYBILL("M-Pesa Paybill"),
  CARD("Card");

  private final String label;

  PaymentMethod(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public boolean isMpesa() {
    return this == MPESA_TILL || this == MPESA_PAYBILL;
  }
}
