package ke.bscodes.possystemfortavuzigadgetsbackend.social;

/**
 * A social network said no or could not be reached. "retryable" is true for network trouble and
 * busy servers (try again later), false for a clear refusal (e.g. an expired link).
 */
public class SocialApiException extends RuntimeException {

  private final boolean retryable;

  public SocialApiException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }
}
