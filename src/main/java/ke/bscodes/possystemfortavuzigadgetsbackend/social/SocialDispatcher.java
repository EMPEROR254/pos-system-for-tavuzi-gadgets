package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends waiting social media posts every 15 seconds. Switched off in tests
 * (app.social.dispatch-enabled=false), which call due()/publishOne() themselves.
 */
@Component
@ConditionalOnProperty(prefix = "app.social", name = "dispatch-enabled", havingValue = "true")
public class SocialDispatcher {

  private static final Logger log = LoggerFactory.getLogger(SocialDispatcher.class);

  private final SocialPublisher publisher;

  public SocialDispatcher(SocialPublisher publisher) {
    this.publisher = publisher;
  }

  @Scheduled(fixedDelay = 15000)
  public void dispatch() {
    // Called through the Spring bean so each publishOne() gets its own transaction.
    for (Long id : publisher.due()) {
      try {
        publisher.publishOne(id);
      } catch (RuntimeException e) {
        log.warn(
            "Social post target {} could not be processed; will retry: {}", id, e.getMessage());
      }
    }
  }
}
