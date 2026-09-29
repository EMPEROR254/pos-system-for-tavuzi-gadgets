package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Permission;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.RolePermissions;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CloseRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CloseResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.CurrentShift;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.ShiftView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cashier shifts: open with a float, close with a cash count. The owner or a manager (VIEW_REPORTS)
 * can close a shift a cashier left open.
 */
@Service
public class ShiftService {

  private final ShiftRepository shifts;
  private final ShiftQueries queries;
  private final Clock clock;

  public ShiftService(ShiftRepository shifts, ShiftQueries queries, Clock clock) {
    this.shifts = shifts;
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Optional<CurrentShift> current(AuthUser me) {
    return shifts
        .findByCashierIdAndClosedAtIsNull(me.id())
        .map(
            s ->
                new CurrentShift(
                    s.getId(),
                    s.getOpenedAt(),
                    s.getOpeningFloat(),
                    queries.cash(me.id(), s.getOpenedAt(), clock.instant()).salesCount()));
  }

  @Transactional
  public CurrentShift open(AuthUser me, java.math.BigDecimal openingFloat) {
    if (shifts.findByCashierIdAndClosedAtIsNull(me.id()).isPresent()) {
      throw ApiException.conflict("Your shift is already open.");
    }
    try {
      Shift s =
          shifts.saveAndFlush(
              new Shift(me.id(), openingFloat.setScale(2, RoundingMode.HALF_UP), clock.instant()));
      return new CurrentShift(s.getId(), s.getOpenedAt(), s.getOpeningFloat(), 0);
    } catch (DataIntegrityViolationException e) {
      // Opened twice at the same moment (double tap on two tills).
      throw ApiException.conflict("Your shift is already open.");
    }
  }

  @Transactional
  public CloseResult close(AuthUser me, long shiftId, CloseRequest r) {
    Shift s = shifts.findById(shiftId).orElseThrow(() -> ApiException.notFound("No such shift."));
    boolean own = s.getCashierId() == me.id();
    if (!own && !RolePermissions.can(me.role(), Permission.VIEW_REPORTS)) {
      throw ApiException.notFound("No such shift.");
    }
    if (!s.isOpen()) {
      throw ApiException.conflict("This shift is already closed.");
    }
    Instant now = clock.instant();
    ShiftCash cash = queries.cash(s.getCashierId(), s.getOpenedAt(), now);
    String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
    s.close(cash, r.countedCash().setScale(2, RoundingMode.HALF_UP), note, me.id(), now);
    shifts.flush();
    return new CloseResult(queries.one(shiftId), cash.salesCount(), cash.mpesa(), cash.card());
  }

  @Transactional(readOnly = true)
  public List<ShiftView> list(Instant from, Instant to, Long cashierId) {
    return queries.opened(from, to, cashierId);
  }
}
