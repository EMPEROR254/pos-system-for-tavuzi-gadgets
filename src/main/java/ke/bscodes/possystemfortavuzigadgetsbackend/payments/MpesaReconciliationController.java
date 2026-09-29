package ke.bscodes.possystemfortavuzigadgetsbackend.payments;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaReconciliationService.ImportResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.payments.MpesaReconciliationService.Report;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/mpesa-reconciliation (owner and manager - VIEW_REPORTS): upload the M-Pesa statement CSV
 * (sent as plain text), then check the typed M-Pesa codes for a date range.
 */
@RestController
@RequestMapping("/api/mpesa-reconciliation")
@PreAuthorize("hasAuthority('VIEW_REPORTS')")
public class MpesaReconciliationController {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
  private static final int MAX_STATEMENT_CHARS = 5_000_000;

  private final MpesaReconciliationService service;
  private final Clock clock;

  public MpesaReconciliationController(MpesaReconciliationService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @PostMapping(
      value = "/statement",
      consumes = {"text/plain", "text/csv"})
  public ImportResult importStatement(
      @AuthenticationPrincipal AuthUser me, @RequestBody String csv) {
    if (csv.length() > MAX_STATEMENT_CHARS) {
      throw ApiException.badRequest("That file is too big. Download one month at a time.");
    }
    return service.importStatement(csv, me.id());
  }

  /** GET /api/mpesa-reconciliation?from=2026-09-01&to=2026-09-28 (Nairobi days; default today). */
  @GetMapping
  public Report reconcile(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    LocalDate start = from == null ? LocalDate.now(clock.withZone(NAIROBI)) : from;
    LocalDate end = to == null ? start : to;
    if (end.isBefore(start) || start.plusDays(92).isBefore(end)) {
      throw ApiException.badRequest("Choose a date range of up to three months.");
    }
    return service.reconcile(
        start.atStartOfDay(NAIROBI).toInstant(), end.plusDays(1).atStartOfDay(NAIROBI).toInstant());
  }
}
