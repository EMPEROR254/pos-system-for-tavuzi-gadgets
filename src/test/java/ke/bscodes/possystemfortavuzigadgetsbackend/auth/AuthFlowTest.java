package ke.bscodes.possystemfortavuzigadgetsbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/** Login, roles and user management end to end, against PostgreSQL. */
@AutoConfigureMockMvc
class AuthFlowTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private MutableClock clock;

  @Test
  void ownerCreatedOnFirstStartCanSignInAndSeeAllPermissions() throws Exception {
    MvcResult result =
        mvc.perform(loginRequest("OWNER", "owner-pass-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("OWNER"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true))
            .andExpect(cookie().path(AuthController.REFRESH_COOKIE, "/api/auth"))
            .andReturn();
    String token = body(result).path("accessToken").asText();

    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("owner"))
        .andExpect(jsonPath("$.permissions.length()").value(10));
  }

  @Test
  void apiNeedsATokenButTheWebAppAndStatusArePublic() throws Exception {
    mvc.perform(get("/api/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get("/api/users").header("Authorization", "Bearer not.a.token"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/status")).andExpect(status().isOk());
    int page = mvc.perform(get("/sales/new")).andReturn().getResponse().getStatus();
    assertThat(page).isNotIn(401, 403);
  }

  @Test
  void cashierIsBlockedFromUserManagement() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    createUser(owner, "Cashier1", "Mary Wanjiru", "CASHIER", "cashier-pass-1")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("cashier1"));

    String cashier = accessToken("cashier1", "cashier-pass-1");
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + cashier))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0]").value("MAKE_SALE"));
    mvc.perform(get("/api/users").header("Authorization", "Bearer " + cashier))
        .andExpect(status().isForbidden());
    createUser(cashier, "sneaky", "Sneaky", "OWNER", "password-123")
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerSearchesUsersAndGetsInlineValidationErrors() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    createUser(owner, "manager.jane", "Jane Akinyi", "MANAGER", "manager-pass")
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/users")
                .param("q", "AKINYI")
                .param("role", "MANAGER")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].fullName").value("Jane Akinyi"));

    createUser(owner, "Manager.Jane", "Other", "CASHIER", "password-123")
        .andExpect(status().isConflict());
    createUser(owner, "x", "", "CASHIER", "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.fullName").exists())
        .andExpect(jsonPath("$.errors.password").value("Use at least 8 characters."));
  }

  @Test
  void tooManyWrongPasswordsLockTheAccountForAWhile() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    createUser(owner, "locky", "Locky", "CASHIER", "right-pass-1").andExpect(status().isCreated());

    for (int i = 0; i < 4; i++) {
      mvc.perform(loginRequest("locky", "wrong")).andExpect(status().isUnauthorized());
    }
    mvc.perform(loginRequest("locky", "wrong")).andExpect(status().isLocked());
    // Even the right password is refused while locked...
    mvc.perform(loginRequest("locky", "right-pass-1")).andExpect(status().isLocked());
    // ...and works again after the lock time.
    clock.advance(Duration.ofMinutes(16));
    mvc.perform(loginRequest("locky", "right-pass-1")).andExpect(status().isOk());
  }

  @Test
  void refreshTokenRotatesAndAReusedOldTokenSignsTheUserOutEverywhere() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    createUser(owner, "rotator", "Rota", "CASHIER", "rotate-pass").andExpect(status().isCreated());
    Cookie first =
        mvc.perform(loginRequest("rotator", "rotate-pass"))
            .andReturn()
            .getResponse()
            .getCookie("refresh_token");

    Cookie second =
        mvc.perform(post("/api/auth/refresh").cookie(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value("rotator"))
            .andReturn()
            .getResponse()
            .getCookie("refresh_token");
    assertThat(second.getValue()).isNotEqualTo(first.getValue());

    // A second tab refreshing with the same token a moment later is normal.
    clock.advance(Duration.ofSeconds(5));
    mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isOk());

    // Long after rotation, the old token shows up again: treat it as stolen.
    clock.advance(Duration.ofMinutes(5));
    mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    Cookie refresh =
        mvc.perform(loginRequest("owner", "owner-pass-123"))
            .andReturn()
            .getResponse()
            .getCookie("refresh_token");
    mvc.perform(post("/api/auth/logout").cookie(refresh))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE, 0));
    mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
  }

  @Test
  void disablingAUserStopsTheirTokenAtOnce() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    long id =
        body(createUser(owner, "leaver", "Leaver", "CASHIER", "leaver-pass").andReturn())
            .path("id")
            .asLong();
    String leaver = accessToken("leaver", "leaver-pass");

    mvc.perform(
            put("/api/users/" + id)
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Leaver\",\"role\":\"CASHIER\",\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + leaver))
        .andExpect(status().isUnauthorized());
    mvc.perform(loginRequest("leaver", "leaver-pass")).andExpect(status().isUnauthorized());
  }

  @Test
  void usersChangeTheirOwnPassword() throws Exception {
    String owner = accessToken("owner", "owner-pass-123");
    createUser(owner, "changer", "Changer", "MANAGER", "old-password")
        .andExpect(status().isCreated());
    String changer = accessToken("changer", "old-password");

    mvc.perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + changer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"nope\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + changer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(loginRequest("changer", "new-password")).andExpect(status().isOk());
  }

  private RequestBuilder loginRequest(String username, String password) throws Exception {
    return post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(new AuthDtos.LoginRequest(username, password)));
  }

  private String accessToken(String username, String password) throws Exception {
    return body(mvc.perform(loginRequest(username, password))
            .andExpect(status().isOk())
            .andReturn())
        .path("accessToken")
        .asText();
  }

  private ResultActions createUser(
      String token, String username, String fullName, String role, String password)
      throws Exception {
    String request =
        json.writeValueAsString(
            Map.of("username", username, "fullName", fullName, "role", role, "password", password));
    return mvc.perform(
        post("/api/users")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request));
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }
}
