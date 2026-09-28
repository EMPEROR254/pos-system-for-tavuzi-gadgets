package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhonesTest {

  @Test
  void acceptsCommonKenyanFormats() {
    assertThat(Phones.normalize("0712 345 678")).isEqualTo("254712345678");
    assertThat(Phones.normalize("+254712345678")).isEqualTo("254712345678");
    assertThat(Phones.normalize("712345678")).isEqualTo("254712345678");
    assertThat(Phones.normalize("0110345678")).isEqualTo("254110345678");
  }

  @Test
  void rejectsOtherNumbers() {
    assertThatThrownBy(() -> Phones.normalize("12345")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Phones.normalize("0202345678"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
