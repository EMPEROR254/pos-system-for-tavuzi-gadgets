package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends due eTIMS invoices every half minute, oldest first, each in its own transaction. When KRA
 * or the internet is down, invoices wait and are retried with growing gaps (see EtimsInvoice).
 * Switched off in tests, which call EtimsService.due()/send() themselves.
 */
@Component
@ConditionalOnProperty(prefix = "app.etims", name = "dispatch-enabled", havingValue = "true")
public class EtimsDispatcher {

  private static final Logger log = LoggerFactory.getLogger(EtimsDispatcher.class);

  private final EtimsService etims;

  public EtimsDispatcher(EtimsService etims) {
    this.etims = etims;
  }

  @Scheduled(fixedDelayString = "${app.etims.dispatch-interval-ms:30000}")
  public void dispatch() {
    for (Long id : etims.due()) {
      try {
        etims.send(id);
      } catch (RuntimeException e) {
        log.warn("eTIMS invoice {} could not be processed; will retry: {}", id, e.getMessage());
      }
    }
  }
}
