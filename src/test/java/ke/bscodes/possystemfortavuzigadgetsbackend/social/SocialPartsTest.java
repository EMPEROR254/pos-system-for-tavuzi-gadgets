package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import org.junit.jupiter.api.Test;

class SocialPartsTest {

  private static SocialProperties props(String key) {
    return new SocialProperties(
        "https://pos.test/", key, null, null, null, null, null, null, null, null, false);
  }

  @Test
  void tokensAreEncryptedAndTamperingIsNoticed() {
    TokenCipher cipher = new TokenCipher(props("a-long-enough-secret-key"));
    String a = cipher.encrypt("page-token");
    assertThat(a).isNotEqualTo(cipher.encrypt("page-token")); // new IV every time
    assertThat(cipher.decrypt(a)).isEqualTo("page-token");

    char[] chars = a.toCharArray();
    chars[20] = chars[20] == 'A' ? 'B' : 'A';
    assertThatThrownBy(() -> cipher.decrypt(new String(chars)))
        .isInstanceOf(IllegalStateException.class);
    // Another key can't read it.
    assertThatThrownBy(() -> new TokenCipher(props("a-different-secret-key")).decrypt(a))
        .isInstanceOf(IllegalStateException.class);
    assertThat(new TokenCipher(props("short")).ready()).isFalse();
  }

  @Test
  void picturesBecomeJpegsOnWhiteAtMost1080Pixels() throws Exception {
    BufferedImage transparent = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(transparent, "png", png);

    ImageProcessor.Prepared p = ImageProcessor.prepare(png.toByteArray());
    assertThat(p.width()).isEqualTo(1080);
    assertThat(p.height()).isEqualTo(540);
    BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(p.jpeg()));
    assertThat(new Color(jpeg.getRGB(10, 10))).isEqualTo(Color.WHITE);
    assertThat(p.jpeg()[0]).isEqualTo((byte) 0xFF); // JPEG starts FF D8
    assertThat(p.jpeg()[1]).isEqualTo((byte) 0xD8);

    assertThatThrownBy(() -> ImageProcessor.prepare("hello".getBytes()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void instagramOnlyTakesPicturesFrom4by5To191by100() {
    ImageProcessor.Prepared portrait = new ImageProcessor.Prepared(new byte[0], 800, 1000);
    ImageProcessor.Prepared tall = new ImageProcessor.Prepared(new byte[0], 500, 1000);
    ImageProcessor.checkFits(portrait, SocialNetwork.INSTAGRAM);
    ImageProcessor.checkFits(tall, SocialNetwork.FACEBOOK);
    assertThatThrownBy(() -> ImageProcessor.checkFits(tall, SocialNetwork.INSTAGRAM))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void captionsJoinTheTextAndTikTokTitlesAreShort() {
    assertThat(Captions.caption("Sale!", "  Earbuds 20% off ", null))
        .isEqualTo("Sale!\n\nEarbuds 20% off");
    assertThat(Captions.caption("Sale!", null, "254712345678"))
        .isEqualTo("Sale!\n\nOrder on WhatsApp: https://wa.me/254712345678");
    assertThat(Captions.tiktokTitle("x".repeat(150))).hasSize(90).endsWith("…");
  }

  @Test
  void publicLinksUseTheShopsAddress() {
    SocialProperties p = props("a-long-enough-secret-key");
    assertThat(p.redirectUri("meta")).isEqualTo("https://pos.test/api/social/callback/meta");
    assertThat(p.imageUrl("abc")).isEqualTo("https://pos.test/api/social/images/abc.jpg");
  }
}
