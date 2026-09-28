package ke.bscodes.possystemfortavuzigadgetsbackend.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

  @Autowired private MockMvc mvc;

  @Test
  void reportsOk() throws Exception {
    mvc.perform(get("/api/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }
}
