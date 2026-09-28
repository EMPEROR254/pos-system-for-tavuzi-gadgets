package ke.bscodes.possystemfortavuzigadgetsbackend.receipt;

import java.io.ByteArrayOutputStream;

/**
 * A small ESC/POS command builder for thermal receipt printers (Epson, Xprinter
 * and most 58mm/80mm printers). Shared module from BS Codes.
 */
public class EscPosBuilder {

  private static final int ESC = 0x1B;
  private static final int GS = 0x1D;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  public enum Align {
    LEFT,
    CENTER,
    RIGHT
  }

  public EscPosBuilder() {
    write(ESC, 0x40); // ESC @ = reset the printer
  }

  public EscPosBuilder align(Align align) {
    write(ESC, 0x61, align.ordinal());
    return this;
  }

  public EscPosBuilder bold(boolean on) {
    write(ESC, 0x45, on ? 1 : 0);
    return this;
  }

  /** Double height and width, for the shop name or totals. */
  public EscPosBuilder large(boolean on) {
    write(GS, 0x21, on ? 0x11 : 0x00);
    return this;
  }

  /** Text followed by a new line. Non-ASCII characters become "?". */
  public EscPosBuilder line(String text) {
    for (char c : text.toCharArray()) {
      bytes.write(c < 128 ? c : '?');
    }
    bytes.write(0x0A);
    return this;
  }

  public EscPosBuilder feed(int lines) {
    write(ESC, 0x64, Math.min(lines, 255));
    return this;
  }

  /** Feed a little, then partial cut (GS V 66 n). */
  public EscPosBuilder cut() {
    write(GS, 0x56, 66, 3);
    return this;
  }

  public byte[] build() {
    return bytes.toByteArray();
  }

  private void write(int... values) {
    for (int v : values) {
      bytes.write(v);
    }
  }
}
