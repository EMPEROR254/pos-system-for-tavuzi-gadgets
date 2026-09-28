package ke.bscodes.possystemfortavuzigadgetsbackend.warranty;

import static org.assertj.core.api.Assertions.assertThat;

import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import org.junit.jupiter.api.Test;

public class SerialsTest {

  /**
   * Makes a valid IMEI from 14 digits by adding the Luhn check digit (also used by other tests).
   */
  public static String imei(String first14) {
    for (int check = 0; check <= 9; check++) {
      if (Serials.luhnValid(first14 + check)) {
        return first14 + check;
      }
    }
    throw new IllegalStateException();
  }

  @Test
  void acceptsRealImeisAndCatchesTypos() {
    assertThat(Serials.problem(SerialType.IMEI, "490154203237518")).isNull();
    assertThat(Serials.problem(SerialType.IMEI, "490154203237519")).contains("not valid");
    assertThat(Serials.problem(SerialType.IMEI, "49015420323751")).contains("15 digits");
  }

  @Test
  void serialNumbersAreNormalisedAndChecked() {
    assertThat(Serials.normalise(" 5cg 123-ab ")).isEqualTo("5CG123-AB");
    assertThat(Serials.problem(SerialType.SERIAL, "5CG123-AB")).isNull();
    assertThat(Serials.problem(SerialType.SERIAL, "X")).isNotNull();
  }
}
