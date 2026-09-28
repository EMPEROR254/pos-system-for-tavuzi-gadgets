package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DarajaPasswordTest {

  @Test
  void timestampIsNairobiTime() {
    assertThat(DarajaPassword.timestamp(Instant.parse("2026-01-15T21:30:05Z")))
        .isEqualTo("20260116003005");
  }

  @Test
  void passwordIsBase64OfShortcodePasskeyTimestamp() {
    String password = DarajaPassword.password("174379", "key", "20260116003005");
    assertThat(new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8))
        .isEqualTo("174379key20260116003005");
  }
}
