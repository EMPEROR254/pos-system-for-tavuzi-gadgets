package ke.bscodes.possystemfortavuzigadgetsbackend.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.sync.SyncService.Catalogue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/sync: what a till needs to keep selling without the server (catalogue with prices, stock and
 * IMEIs on the shelf), and how much is still waiting to go out to KRA and the SMS provider.
 */
@RestController
@RequestMapping("/api/sync")
@PreAuthorize("hasAuthority('MAKE_SALE')")
public class SyncController {

  private final SyncService service;
  private final Clock clock;

  public SyncController(SyncService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @GetMapping("/catalogue")
  public Catalogue catalogue() {
    return service.catalogue();
  }

  /** Waiting eTIMS invoices and SMS - these go out by themselves once the internet is back. */
  @GetMapping("/status")
  public Map<String, Object> status() {
    SyncService.Waiting w = service.waiting();
    return Map.of(
        "serverTime", Instant.now(clock),
        "etimsMode", w.etimsMode(),
        "etimsPending", w.etimsPending(),
        "etimsRejected", w.etimsRejected(),
        "smsQueued", w.smsQueued());
  }
}
