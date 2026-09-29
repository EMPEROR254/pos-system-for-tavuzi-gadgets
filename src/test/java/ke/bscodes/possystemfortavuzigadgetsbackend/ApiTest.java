package ke.bscodes.possystemfortavuzigadgetsbackend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Integration tests that call the API: sign-in, JSON requests and small builders. */
@AutoConfigureMockMvc
public abstract class ApiTest extends IntegrationTest {

  /** Unique suffixes so tests sharing one database never clash. */
  protected static final AtomicInteger SEQ = new AtomicInteger(5000);

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;

  protected String owner() throws Exception {
    return login("owner", "owner-pass-123");
  }

  /** Creates a user with the role and returns their access token. */
  protected String newUser(String role) throws Exception {
    String name = role.toLowerCase() + SEQ.incrementAndGet();
    post(
            owner(),
            "/api/users",
            Map.of(
                "username",
                name,
                "fullName",
                "Test " + name,
                "role",
                role,
                "password",
                "password-" + name))
        .andExpect(status().isCreated());
    return login(name, "password-" + name);
  }

  protected String login(String username, String password) throws Exception {
    return body(mvc.perform(
                MockMvcRequestBuilders.post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", password))))
            .andExpect(status().isOk()))
        .path("accessToken")
        .asText();
  }

  protected ResultActions post(String token, String path, Object body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(path)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  protected ResultActions put(String token, String path, Object body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.put(path)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  protected ResultActions get(String token, String path) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token));
  }

  protected JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** A product with a Piece unit (and optionally a bigger unit); returns its JSON. */
  protected JsonNode createProduct(
      String token, String name, String serialType, int retail, Map<String, Object> extraUnit)
      throws Exception {
    List<Map<String, Object>> units =
        extraUnit == null
            ? List.of(Map.of("name", "Piece", "factor", 1, "retailPrice", retail))
            : List.of(Map.of("name", "Piece", "factor", 1, "retailPrice", retail), extraUnit);
    Map<String, Object> p = new HashMap<>();
    p.put("name", name + " #" + SEQ.incrementAndGet());
    p.put("category", "Test " + serialType);
    p.put("vatCategory", "STANDARD");
    p.put("serialType", serialType);
    p.put("warrantyMonths", serialType.equals("NONE") ? 0 : 12);
    p.put("costPrice", 0);
    p.put("active", true);
    p.put("units", units);
    return body(post(token, "/api/products", p).andExpect(status().isCreated()));
  }

  /** A cash sale body for the till. */
  protected static Map<String, Object> cashSale(
      long productId, long unitId, int qty, int total, List<String> serials) {
    Map<String, Object> line = new HashMap<>();
    line.put("productId", productId);
    line.put("unitId", unitId);
    line.put("quantity", qty);
    line.put("serials", serials);
    Map<String, Object> sale = new HashMap<>();
    sale.put("clientRef", UUID.randomUUID().toString());
    sale.put("priceTier", "RETAIL");
    sale.put("lines", List.of(line));
    sale.put("payments", List.of(Map.of("method", "CASH", "amount", total)));
    return sale;
  }
}
