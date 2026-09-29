package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.util.Optional;

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

  /** The SMS account balance as the provider shows it (e.g. "KES 1,234.50"), if known. */
  default Optional<String> balance() {
    return Optional.empty();
  }
}
