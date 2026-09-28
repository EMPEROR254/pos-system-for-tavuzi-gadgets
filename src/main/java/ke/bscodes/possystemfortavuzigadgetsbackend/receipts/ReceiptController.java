package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receipts: GET /api/sales/{id}/receipt (for the screen and browser printing) and POST
 * /api/sales/{id}/receipt/print (to the thermal printer, when set up).
 */
@RestController
@RequestMapping("/api/sales/{id}/receipt")
@PreAuthorize("hasAuthority('MAKE_SALE')")
public class ReceiptController {

  private final ReceiptService service;

  public ReceiptController(ReceiptService service) {
    this.service = service;
  }

  @GetMapping
  public ReceiptView receipt(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    return service.receipt(me, id);
  }

  @PostMapping("/print")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void print(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    service.print(me, id);
  }
}
