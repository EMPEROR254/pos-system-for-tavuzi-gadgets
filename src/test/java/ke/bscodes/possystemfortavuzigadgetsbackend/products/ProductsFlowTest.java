package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Catalogue rules: one base unit, unique SKU, unit ids kept on edit, search and paging. */
@AutoConfigureMockMvc
class ProductsFlowTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  private String owner;

  @BeforeEach
  void signIn() throws Exception {
    owner =
        json.readTree(
                mvc.perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"owner-pass-123\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("accessToken")
            .asText();
  }

  private Map<String, Object> product(String name, String sku, List<Map<String, Object>> units) {
    Map<String, Object> p = new HashMap<>();
    p.put("name", name);
    p.put("sku", sku);
    p.put("category", "Chargers");
    p.put("vatCategory", "STANDARD");
    p.put("serialType", "NONE");
    p.put("warrantyMonths", 3);
    p.put("costPrice", 300);
    p.put("active", true);
    p.put("units", units);
    return p;
  }

  private static Map<String, Object> unit(Long id, String name, int factor, int price) {
    Map<String, Object> u = new HashMap<>();
    u.put("id", id);
    u.put("name", name);
    u.put("factor", factor);
    u.put("retailPrice", price);
    return u;
  }

  @Test
  void productNeedsExactlyOneBaseUnitAndAUniqueSku() throws Exception {
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        product("Charger", "CHG-X", List.of(unit(null, "Box", 10, 5000))))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("factor of 1")));

    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        product("Charger 20W", "CHG-20", List.of(unit(null, "Piece", 1, 650))))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        product("Other", "chg-20", List.of(unit(null, "Piece", 1, 650))))))
        .andExpect(status().isConflict());
  }

  @Test
  void editingKeepsUnitIdsAndSearchFindsIt() throws Exception {
    JsonNode created =
        json.readTree(
            mvc.perform(
                    post("/api/products")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                product(
                                    "Power bank 10000mAh",
                                    "PB-10K",
                                    List.of(
                                        unit(null, "Piece", 1, 1800),
                                        unit(null, "Box of 10", 10, 16000))))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long id = created.path("id").asLong();
    long pieceId = created.path("units").get(0).path("id").asLong();

    JsonNode updated =
        json.readTree(
            mvc.perform(
                    put("/api/products/" + id)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                product(
                                    "Power bank 10000mAh",
                                    "PB-10K",
                                    List.of(unit(pieceId, "Piece", 1, 1900))))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(updated.path("units")).hasSize(1);
    assertThat(updated.path("units").get(0).path("id").asLong()).isEqualTo(pieceId);
    assertThat(updated.path("units").get(0).path("retailPrice").decimalValue())
        .isEqualByComparingTo("1900");

    mvc.perform(
            get("/api/products")
                .param("q", "power BANK")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Power bank 10000mAh"))
        .andExpect(jsonPath("$.content[0].costPrice").value(300.00));
  }
}
