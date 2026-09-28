package ke.bscodes.possystemfortavuzigadgetsbackend.common;

import org.springframework.http.HttpStatus;

/**
 * A business-rule failure with a message that is safe to show to staff, e.g. "That username is
 * already taken" (409). ApiExceptionHandler turns it into a ProblemDetail.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }
}
