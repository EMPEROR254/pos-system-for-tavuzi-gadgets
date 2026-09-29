package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no SMS account is set up: writes the message to the log instead of sending it. */
public class LoggingSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);
  private final AtomicLong ids = new AtomicLong();

  @Override
  public Result send(String phone, String message) {
    // Phone numbers are masked in logs (2547****5678).
    log.info("SMS (not sent - no AT_API_KEY) to {}: {}", mask(phone), message);
    return Result.ok("log-" + ids.incrementAndGet());
  }

  static String mask(String phone) {
    return phone.length() < 8
        ? "****"
        : phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
  }
}
