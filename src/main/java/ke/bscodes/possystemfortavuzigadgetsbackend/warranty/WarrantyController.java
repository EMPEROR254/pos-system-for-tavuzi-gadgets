package ke.bscodes.possystemfortavuzigadgetsbackend.warranty;

import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.WarrantyService.WarrantyResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/warranty?q=IMEI, serial or receipt number - for staff at the counter. */
@RestController
@RequestMapping("/api/warranty")
@PreAuthorize("hasAuthority('MAKE_SALE')")
public class WarrantyController {

  private final WarrantyService service;

  public WarrantyController(WarrantyService service) {
    this.service = service;
  }

  @GetMapping
  public List<WarrantyResult> lookup(@RequestParam(defaultValue = "") String q) {
    return service.lookup(q);
  }
}
