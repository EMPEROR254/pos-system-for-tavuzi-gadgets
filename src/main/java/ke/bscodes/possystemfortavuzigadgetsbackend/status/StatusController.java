package ke.bscodes.possystemfortavuzigadgetsbackend.status;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A tiny endpoint to check the service is up: GET /api/status. */
@RestController
public class StatusController {

  @GetMapping("/api/status")
  public Map<String, String> status() {
    return Map.of("service", "pos-system-for-tavuzi-gadgets-backend", "status", "ok");
  }
}
