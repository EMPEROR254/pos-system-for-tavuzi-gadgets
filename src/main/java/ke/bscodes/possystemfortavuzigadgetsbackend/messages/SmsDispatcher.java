package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends queued SMS every few seconds, each in its own transaction, so one failure never blocks the
 * rest. Failed messages are retried on the next runs (3 tries in all). Switched off in tests
 * (app.sms.dispatch-enabled=false), which call queued()/sendOne() themselves.
 */
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "dispatch-enabled", havingValue = "true")
public class SmsDispatcher {

  private static final Logger log = LoggerFactory.getLogger(SmsDispatcher.class);

  private final MessageService messages;

  public SmsDispatcher(MessageService messages) {
    this.messages = messages;
  }

  @Scheduled(fixedDelayString = "${app.sms.dispatch-interval-ms:10000}")
  public void dispatch() {
    // Called through the Spring bean so each sendOne() gets its own transaction.
    for (Long id : messages.queued()) {
      try {
        messages.sendOne(id);
      } catch (RuntimeException e) {
        log.warn("SMS {} could not be processed; will retry: {}", id, e.getMessage());
      }
    }
  }
}
