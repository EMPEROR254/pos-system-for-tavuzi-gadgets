package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import jakarta.validation.Valid;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CloseRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CloseResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CurrentShift;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.OpenRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/shifts: the till opens and closes the cashier's shift (MAKE_SALE). The owner's cash-up list
 * is a report: /api/reports/shifts.
 */
@RestController
@RequestMapping("/api/shifts")
@PreAuthorize("hasAuthority('MAKE_SALE')")
public class ShiftController {

  private final ShiftService service;

  public ShiftController(ShiftService service) {
    this.service = service;
  }

  /** The open shift, or 204 when the cashier has none open. */
  @GetMapping("/current")
  public ResponseEntity<CurrentShift> current(@AuthenticationPrincipal AuthUser me) {
    return service
        .current(me)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/open")
  @ResponseStatus(HttpStatus.CREATED)
  public CurrentShift open(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody OpenRequest request) {
    return service.open(me, request.openingFloat());
  }

  @PostMapping("/{id}/close")
  public CloseResult close(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable long id,
      @Valid @RequestBody CloseRequest request) {
    return service.close(me, id, request);
  }
}
