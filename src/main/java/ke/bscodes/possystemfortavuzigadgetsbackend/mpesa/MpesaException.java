package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

/** Something went wrong talking to M-Pesa. */
public class MpesaException extends RuntimeException {

  public MpesaException(String message) {
    super(message);
  }
}
