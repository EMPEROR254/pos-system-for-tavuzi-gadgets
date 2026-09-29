package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

/** Sends one SMS. phone is 2547XXXXXXXX. */
public interface SmsSender {

  record Result(boolean sent, String providerMessageId, String error) {
    static Result ok(String id) {
      return new Result(true, id, null);
    }

    static Result failed(String error) {
      return new Result(false, null, error);
    }
  }

  Result send(String phone, String message);
}
