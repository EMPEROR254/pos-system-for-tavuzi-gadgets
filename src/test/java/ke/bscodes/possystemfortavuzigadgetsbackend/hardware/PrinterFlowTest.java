package ke.bscodes.possystemfortavuzigadgetsbackend.hardware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Hardware end to end with a fake network printer (a local TCP socket, like a thermal printer on
 * port 9100): settings, test page, receipt printing and the cash drawer pulse.
 */
class PrinterFlowTest extends ApiTest {

  /** Accepts one connection and returns everything the app sent to the "printer". */
  private static CompletableFuture<byte[]> fakePrinter(ServerSocket server) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (Socket socket = server.accept();
              InputStream in = socket.getInputStream()) {
            return in.readAllBytes();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private static Map<String, Object> settings(int port, boolean drawer) {
    Map<String, Object> s = new HashMap<>();
    s.put("host", "127.0.0.1");
    s.put("port", port);
    s.put("width", 48);
    s.put("openDrawer", drawer);
    s.put("autoPrint", true);
    return s;
  }

  private static boolean hasDrawerKick(byte[] bytes) {
    byte[] kick = PrinterService.DRAWER_KICK;
    for (int i = 0; i + kick.length <= bytes.length; i++) {
      if (Arrays.equals(Arrays.copyOfRange(bytes, i, i + kick.length), kick)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void ownerSetsUpThePrinterAndPrintsATestPage() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    try (ServerSocket server = new ServerSocket(0)) {
      put(cashier, "/api/hardware/printer", settings(server.getLocalPort(), true))
          .andExpect(status().isForbidden());
      put(owner, "/api/hardware/printer", settings(server.getLocalPort(), true))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.host").value("127.0.0.1"));
      get(cashier, "/api/hardware/printer").andExpect(jsonPath("$.autoPrint").value(true));

      CompletableFuture<byte[]> printed = fakePrinter(server);
      post(owner, "/api/hardware/printer/test", Map.of()).andExpect(status().isNoContent());
      byte[] bytes = printed.get(5, TimeUnit.SECONDS);
      assertThat(new String(bytes, StandardCharsets.US_ASCII)).contains("Printer test");
      assertThat(hasDrawerKick(bytes)).isTrue();

      Map<String, Object> bad = settings(server.getLocalPort(), false);
      bad.put("width", 40);
      put(owner, "/api/hardware/printer", bad).andExpect(status().isBadRequest());
    }
  }

  @Test
  void cashSaleReceiptPrintsAndOpensTheDrawer() throws Exception {
    String owner = owner();
    String cashier = newUser("CASHIER");
    JsonNode item = createProduct(owner, "Mouse", "NONE", 800, null);
    JsonNode sale =
        body(
            post(
                    cashier,
                    "/api/sales",
                    cashSale(
                        item.path("id").asLong(),
                        item.path("units").get(0).path("id").asLong(),
                        1,
                        1000,
                        null))
                .andExpect(status().isCreated()));
    try (ServerSocket server = new ServerSocket(0)) {
      put(owner, "/api/hardware/printer", settings(server.getLocalPort(), true))
          .andExpect(status().isOk());
      CompletableFuture<byte[]> printed = fakePrinter(server);
      post(cashier, "/api/sales/" + sale.path("id").asLong() + "/receipt/print", Map.of())
          .andExpect(status().isNoContent());
      byte[] bytes = printed.get(5, TimeUnit.SECONDS);
      assertThat(new String(bytes, StandardCharsets.US_ASCII))
          .contains(sale.path("receiptNo").asText())
          .contains("Change");
      assertThat(hasDrawerKick(bytes)).isTrue();
    }
    // Printer switched off (nothing listening on that port any more): a clear 503.
    post(cashier, "/api/sales/" + sale.path("id").asLong() + "/receipt/print", Map.of())
        .andExpect(status().isServiceUnavailable());
  }

  /** Other tests share the database and expect "no printer set up". */
  @AfterEach
  void removePrinter() throws Exception {
    Map<String, Object> none = settings(9100, false);
    none.put("host", "");
    put(owner(), "/api/hardware/printer", none).andExpect(status().isOk());
  }
}
