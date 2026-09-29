package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for KRA's eTIMS OSCU API on a local port: records what it receives and answers like
 * KRA. Tests switch it to "down" (HTTP 503) or "reject" (resultCd not 000).
 */
public class FakeKra {

  public record Request(String path, String tin, String cmcKey, JsonNode body) {}

  private static final ObjectMapper JSON = new ObjectMapper();

  public final List<Request> requests = new CopyOnWriteArrayList<>();
  public volatile String mode = "ok";
  private final AtomicInteger receiptNo = new AtomicInteger(100);
  private final HttpServer server;

  public FakeKra() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.createContext(
        "/",
        exchange -> {
          byte[] in = exchange.getRequestBody().readAllBytes();
          String path = exchange.getRequestURI().getPath();
          requests.add(
              new Request(
                  path,
                  exchange.getRequestHeaders().getFirst("tin"),
                  exchange.getRequestHeaders().getFirst("cmcKey"),
                  JSON.readTree(in)));
          String reply;
          int status = 200;
          if ("down".equals(mode)) {
            status = 503;
            reply = "{}";
          } else if ("reject".equals(mode) && path.endsWith("saveSales")) {
            reply =
                "{\"resultCd\":\"910\",\"resultMsg\":\"Invalid item class code\",\"data\":null}";
          } else if (path.endsWith("saveSales")) {
            int n = receiptNo.incrementAndGet();
            reply =
                "{\"resultCd\":\"000\",\"resultMsg\":\"It is succeeded\",\"data\":{\"curRcptNo\":"
                    + n
                    + ",\"totRcptNo\":"
                    + n
                    + ",\"intrlData\":\"INTRL"
                    + n
                    + "\",\"rcptSign\":\"SIGN"
                    + n
                    + "\",\"sdcDateTime\":\"20260928101500\","
                    + "\"sdcId\":\"KRACU0100000001\",\"mrcNo\":\"WIS00000001\"}}";
          } else {
            reply = "{\"resultCd\":\"000\",\"resultMsg\":\"It is succeeded\",\"data\":null}";
          }
          byte[] out = reply.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<Request> to(String pathEnd) {
    return requests.stream().filter(r -> r.path().endsWith(pathEnd)).toList();
  }
}
