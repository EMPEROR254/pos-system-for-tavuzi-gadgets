package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import jakarta.validation.Valid;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.CreateUserRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.ResetPasswordRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.UpdateUserRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff accounts management: /api/users (owner only - MANAGE_USERS). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('MANAGE_USERS')")
public class UserController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE = Set.of("username", "fullName", "role", "createdAt");

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  /** GET /api/users?q=jane&role=CASHIER&active=true&page=0&size=20&sort=fullName&dir=asc */
  @GetMapping
  public PageResponse<UserResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "fullName") String sort,
      @RequestParam(defaultValue = "asc") String dir) {
    // Only known columns can be sorted on; anything else falls back to the name.
    String column = SORTABLE.contains(sort) ? sort : "fullName";
    Sort order =
        Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, column);
    PageRequest pageable =
        PageRequest.of(
            Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), order.and(Sort.by("id")));
    return PageResponse.of(service.search(q, role, active, pageable));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  public UserResponse update(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable long id,
      @Valid @RequestBody UpdateUserRequest request) {
    return service.update(me, id, request);
  }

  @PostMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(
      @PathVariable long id, @Valid @RequestBody ResetPasswordRequest request) {
    service.resetPassword(id, request.password());
  }
}
