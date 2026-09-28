package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import java.time.Clock;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.PasswordHasher;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On the very first start (no users at all) creates the owner account from OWNER_USERNAME /
 * OWNER_PASSWORD, so nobody has to touch the database by hand. Does nothing once any user exists.
 */
@Component
public class BootstrapOwner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapOwner.class);

  @ConfigurationProperties(prefix = "app.bootstrap-owner")
  public record Settings(String username, String password, String fullName) {}

  private final UserRepository users;
  private final Settings settings;
  private final Clock clock;

  public BootstrapOwner(UserRepository users, Settings settings, Clock clock) {
    this.users = users;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count() > 0) {
      return;
    }
    String username = UserService.normaliseUsername(settings.username());
    if (username.isEmpty() || settings.password() == null || settings.password().length() < 8) {
      log.warn(
          "No users yet: set OWNER_USERNAME and OWNER_PASSWORD (8+ characters) to create the owner");
      return;
    }
    String fullName =
        settings.fullName() == null || settings.fullName().isBlank()
            ? "Owner"
            : settings.fullName();
    users.save(
        new User(
            username,
            fullName,
            PasswordHasher.hash(settings.password()),
            Role.OWNER,
            clock.instant()));
    log.info(
        "Created the owner account '{}' - change its password after the first login", username);
  }
}
