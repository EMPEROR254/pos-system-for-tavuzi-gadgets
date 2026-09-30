package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;

/**
 * Gets a picture ready for every network: JPEG (Instagram accepts nothing else), at most 1080 px on
 * the longest side (TikTok's photo limit, Instagram's standard width), transparent parts on white.
 */
public final class ImageProcessor {

  public record Prepared(byte[] jpeg, int width, int height) {}

  static final int MAX_SIDE = 1080;
  // Instagram only takes pictures between 4:5 (portrait) and 1.91:1 (landscape).
  static final double INSTAGRAM_MIN_RATIO = 0.8;
  static final double INSTAGRAM_MAX_RATIO = 1.91;

  private ImageProcessor() {}

  public static Prepared prepare(byte[] upload) {
    BufferedImage source;
    try {
      source = ImageIO.read(new ByteArrayInputStream(upload));
    } catch (IOException e) {
      source = null;
    }
    if (source == null) {
      throw ApiException.badRequest("Use a JPEG or PNG picture.");
    }
    double scale =
        Math.min(1.0, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

    BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();
    return new Prepared(jpeg(rgb), width, height);
  }

  /** Instagram refuses pictures that are too tall or too wide; say so before posting. */
  public static void checkFits(Prepared image, SocialNetwork network) {
    double ratio = (double) image.width() / image.height();
    if (network == SocialNetwork.INSTAGRAM
        && (ratio < INSTAGRAM_MIN_RATIO - 0.005 || ratio > INSTAGRAM_MAX_RATIO + 0.005)) {
      throw ApiException.badRequest(
          "Instagram needs a picture between 4:5 (portrait) and 1.91:1 (landscape). Crop it, or"
              + " untick Instagram.");
    }
  }

  private static byte[] jpeg(BufferedImage image) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.9f);
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
      writer.dispose();
      stream.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not save the picture", e);
    }
  }
}
