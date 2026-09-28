package ke.bscodes.possystemfortavuzigadgetsbackend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: the whole app against a real PostgreSQL in Docker (Testcontainers),
 * with Flyway migrations and a clock the test can move. The container is shared by all tests in the
 * run.
 */
@SpringBootTest
@Import(IntegrationTest.TestClockConfig.class)
@TestPropertySource(
    properties = {
      "app.auth.jwt-secret=integration-test-secret-that-is-long-enough",
      "app.bootstrap-owner.username=owner",
      "app.bootstrap-owner.password=owner-pass-123",
      "app.bootstrap-owner.full-name=Test Owner"
    })
public abstract class IntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  /** A clock tests can move forward, e.g. to expire a lock or a token. */
  public static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-28T07:00:00Z");

    public void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }
}
