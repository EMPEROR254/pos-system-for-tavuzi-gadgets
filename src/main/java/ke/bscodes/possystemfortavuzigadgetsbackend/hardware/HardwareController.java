package ke.bscodes.possystemfortavuzigadgetsbackend.hardware;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/hardware/printer: every till reads the settings (to auto-print after a sale); only the owner
 * changes them or prints a test page (MANAGE_SETTINGS).
 */
@RestController
@RequestMapping("/api/hardware/printer")
public class HardwareController {

  private final PrinterService printer;

  public HardwareController(PrinterService printer) {
    this.printer = printer;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('MAKE_SALE')")
  public PrinterConfig get() {
    return printer.current();
  }

  @PutMapping
  @PreAuthorize("hasAuthority('MANAGE_SETTINGS')")
  public PrinterConfig update(@Valid @RequestBody PrinterConfig config) {
    return printer.update(config);
  }

  @PostMapping("/test")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('MANAGE_SETTINGS')")
  public void test() {
    printer.testPrint();
  }
}
