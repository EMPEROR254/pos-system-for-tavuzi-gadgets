package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.CheckoutRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.ReturnRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.ReturnView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/sales: checkout, the sales list, one sale, and returns. */
@RestController
@RequestMapping("/api/sales")
@PreAuthorize("hasAuthority('MAKE_SALE')")
public class SaleController {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_RANGE_DAYS = 366;

  private final SaleService sales;
  private final ReturnService returns;
  private final Clock clock;

  public SaleController(SaleService sales, ReturnService returns, Clock clock) {
    this.sales = sales;
    this.returns = returns;
    this.clock = clock;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SaleView checkout(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody CheckoutRequest request) {
    return sales.checkout(me, request);
  }

  /** GET /api/sales?from=2026-09-01&to=2026-09-28 (days in Nairobi time; default today). */
  @GetMapping
  public PageResponse<SaleSummary> list(
      @AuthenticationPrincipal AuthUser me,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long cashierId,
      @RequestParam(required = false) String receipt,
      @RequestParam(required = false) SaleStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    LocalDate today = LocalDate.now(clock.withZone(NAIROBI));
    LocalDate start = from == null ? today : from;
    LocalDate end = to == null ? start : to;
    if (end.isBefore(start) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
      throw ApiException.badRequest("Choose a date range of up to one year.");
    }
    PageRequest pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    return PageResponse.of(
        sales.list(
            me,
            start.atStartOfDay(NAIROBI).toInstant(),
            end.plusDays(1).atStartOfDay(NAIROBI).toInstant(),
            cashierId,
            receipt,
            status,
            pageable));
  }

  @GetMapping("/{id}")
  public SaleView get(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    return sales.get(me, id);
  }

  /** Needs the owner: signed in, or approving on the till with their password. */
  @PostMapping("/{id}/returns")
  @ResponseStatus(HttpStatus.CREATED)
  public ReturnView returnItems(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable long id,
      @Valid @RequestBody ReturnRequest request) {
    return returns.process(me, id, request);
  }
}
