package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for Facebook's Graph API (/graph/...) and TikTok's API (/tiktok/...) on a local port.
 * Records every request and answers like the real services. "facebook" can be switched to "down"
 * (HTTP 503) or "refuse" (HTTP 400 with Facebook's error shape); Instagram's picture status can be
 * set to IN_PROGRESS to test waiting.
 */
public class FakeSocial {

  public record Request(
      String method, String path, String query, String authorization, String body) {}

  public final List<Request> requests = new CopyOnWriteArrayList<>();
  public volatile String facebook = "ok";
  public volatile String instagramStatus = "FINISHED";
  private final HttpServer server;

  public FakeSocial() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.createContext("/", this::handle);
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<Request> to(String pathEnd) {
    return requests.stream().filter(r -> r.path().endsWith(pathEnd)).toList();
  }

  private void handle(HttpExchange ex) throws IOException {
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
    String path = ex.getRequestURI().getPath();
    String query = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
    requests.add(
        new Request(
            ex.getRequestMethod(),
            path,
            query,
            ex.getRequestHeaders().getFirst("Authorization"),
            body));
    int status = 200;
    String reply;
    if (path.equals("/graph/oauth/access_token")) {
      reply =
          query.contains("fb_exchange_token")
              ? "{\"access_token\":\"long-user-token\"}"
              : "{\"access_token\":\"short-user-token\"}";
    } else if (path.equals("/graph/me/accounts")) {
      reply =
          "{\"data\":[{\"id\":\"111\",\"name\":\"Tavuzi Gadgets\",\"access_token\":\"page-token-1\","
              + "\"instagram_business_account\":{\"id\":\"999\",\"username\":\"tavuzigadgets\"}}]}";
    } else if (path.equals("/graph/111/photos")) {
      if ("down".equals(facebook)) {
        status = 503;
        reply = "{}";
      } else if ("refuse".equals(facebook)) {
        status = 400;
        reply = "{\"error\":{\"message\":\"(#200) The Page token has expired\"}}";
      } else {
        reply = "{\"id\":\"photo-1\",\"post_id\":\"111_222\"}";
      }
    } else if (path.equals("/graph/999/media")) {
      reply = "{\"id\":\"container-1\"}";
    } else if (path.equals("/graph/container-1")) {
      reply = "{\"status_code\":\"" + instagramStatus + "\"}";
    } else if (path.equals("/graph/999/media_publish")) {
      reply = "{\"id\":\"ig-media-1\"}";
    } else if (path.equals("/tiktok/v2/oauth/token/")) {
      boolean refresh = body.contains("grant_type=refresh_token");
      reply =
          "{\"access_token\":\""
              + (refresh ? "tt-access-2" : "tt-access")
              + "\",\"expires_in\":86400,\"open_id\":\"tt-open\","
              + "\"refresh_token\":\"tt-refresh\",\"refresh_expires_in\":31536000}";
    } else if (path.equals("/tiktok/v2/user/info/")) {
      reply =
          "{\"data\":{\"user\":{\"display_name\":\"Tavuzi on TikTok\"}},\"error\":{\"code\":\"ok\"}}";
    } else if (path.equals("/tiktok/v2/post/publish/content/init/")) {
      reply =
          "{\"data\":{\"publish_id\":\"p_pub_1\"},\"error\":{\"code\":\"ok\",\"message\":\"\"}}";
    } else {
      status = 404;
      reply = "{\"error\":{\"message\":\"Unknown path " + path + "\"}}";
    }
    byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = ex.getResponseBody()) {
      out.write(bytes);
    }
  }
}
