package ke.bscodes.possystemfortavuzigadgetsbackend.hardware;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipt.EscPosBuilder;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipt.NetworkPrinter;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.PrinterProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ShopProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shop's network receipt printer (ESC/POS over TCP port 9100, sent with the shared
 * NetworkPrinter). Settings saved on the Hardware screen win over the .env defaults. The cash
 * drawer plugs into the printer (RJ11) and opens with an ESC/POS pulse.
 */
@Service
public class PrinterService {

  private static final Logger log = LoggerFactory.getLogger(PrinterService.class);
  private static final Set<Integer> WIDTHS = Set.of(32, 42, 48);

  /** ESC p 0 25 250: pulse drawer pin 2 (the usual cash drawer socket). */
  static final byte[] DRAWER_KICK = {0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Nairobi"));

  private final PrinterSettingsRepository settings;
  private final PrinterProperties defaults;
  private final ShopProperties shop;
  private final Clock clock;

  public PrinterService(
      PrinterSettingsRepository settings,
      PrinterProperties defaults,
      ShopProperties shop,
      Clock clock) {
    this.settings = settings;
    this.defaults = defaults;
    this.shop = shop;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PrinterConfig current() {
    return settings
        .findById(1)
        .map(
            s ->
                new PrinterConfig(
                    s.getHost(), s.getPort(), s.getWidth(), s.isOpenDrawer(), s.isAutoPrint()))
        .orElseGet(
            () ->
                new PrinterConfig(
                    defaults.host(),
                    defaults.portOrDefault(),
                    defaults.widthOrDefault(),
                    false,
                    false));
  }

  @Transactional
  public PrinterConfig update(PrinterConfig c) {
    if (!WIDTHS.contains(c.width())) {
      throw ApiException.badRequest("Paper width must be 48 (80mm), 42 or 32 (58mm) characters.");
    }
    PrinterSettings s = settings.findById(1).orElseGet(() -> new PrinterSettings(clock.instant()));
    String host = c.host() == null || c.host().isBlank() ? null : c.host().trim();
    s.update(host, c.port(), c.width(), c.openDrawer(), c.autoPrint(), clock.instant());
    settings.save(s);
    return current();
  }

  /** Prints bytes made for this printer's width; opens the drawer first when asked. */
  public void print(byte[] receipt, boolean openDrawer) {
    PrinterConfig c = current();
    if (!c.configured()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "No receipt printer is set up. Use the browser's Print instead.");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (openDrawer && c.openDrawer()) {
      out.writeBytes(DRAWER_KICK);
    }
    out.writeBytes(receipt);
    send(c, out.toByteArray());
  }

  /** A short test page (and a drawer pulse if the drawer is switched on). */
  public void testPrint() {
    PrinterConfig c = current();
    String rule = "-".repeat(c.width());
    byte[] page =
        new EscPosBuilder()
            .align(EscPosBuilder.Align.CENTER)
            .bold(true)
            .line(shop.name())
            .bold(false)
            .line("Printer test " + TIME.format(clock.instant()))
            .line(rule)
            .line("If you can read this, the printer works.")
            .line(c.width() + " characters per line:")
            .line("1234567890".repeat(5).substring(0, c.width()))
            .feed(3)
            .cut()
            .build();
    print(page, true);
  }

  private void send(PrinterConfig c, byte[] bytes) {
    try {
      NetworkPrinter.print(c.host(), c.port(), bytes);
    } catch (IOException e) {
      log.warn("Receipt printer {}:{} did not answer: {}", c.host(), c.port(), e.getMessage());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "The receipt printer did not answer. Check it is on, has paper and the IP is right.");
    }
  }
}
