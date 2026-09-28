package ke.bscodes.possystemfortavuzigadgetsbackend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.RolePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every API error is an RFC 7807 ProblemDetail. Validation errors also carry "errors": {field:
 * message} so the web app can show them next to each input.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ProblemDetail handleApi(ApiException e) {
    return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
  }

  @ExceptionHandler({AccessDeniedException.class, RolePermissions.ForbiddenException.class})
  ProblemDetail handleForbidden(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Your role may not do this.");
  }

  /** Two tills saving the same thing at the same moment (e.g. one M-Pesa code twice). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleConflict(DataIntegrityViolationException e) {
    log.warn("Conflicting save: {}", e.getMostSpecificCause().getMessage());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "This was just changed or used elsewhere. Please check and try again.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception e) {
    log.error("Unexpected error", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Please check the highlighted fields.");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }
}
