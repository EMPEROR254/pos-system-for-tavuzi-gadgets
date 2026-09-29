package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService.InvoiceView;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService.Summary;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/etims: invoices sent to KRA, waiting or refused (owner and manager - VIEW_REPORTS). */
@RestController
@RequestMapping("/api/etims")
@PreAuthorize("hasAuthority('VIEW_REPORTS')")
public class EtimsController {

  private final EtimsService service;

  public EtimsController(EtimsService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public Summary summary() {
    return service.summary();
  }

  @GetMapping("/invoices")
  public PageResponse<InvoiceView> invoices(
      @RequestParam(required = false) EtimsInvoice.Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        service.list(status, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100))));
  }

  /** Send a refused invoice again, after fixing the reason KRA gave. */
  @PostMapping("/invoices/{id}/retry")
  public InvoiceView retry(@PathVariable long id) {
    return service.retry(id);
  }
}
