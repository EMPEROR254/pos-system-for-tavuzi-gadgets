package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Map;
import javax.imageio.ImageIO;
import ke.bscodes.possystemfortavuzigadgetsbackend.ApiTest;
import ke.bscodes.possystemfortavuzigadgetsbackend.IntegrationTest.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Social media end to end against a fake Facebook/Instagram/TikTok: linking through their sign-in
 * pages, posting a picture with text to all three in the background, Instagram's wait for the
 * picture, failures and retries, and who may do what.
 */
class SocialFlowTest extends ApiTest {

  static final FakeSocial FAKE = new FakeSocial();
  static final String PUBLIC_URL = "https://pos.tavuzi.test";

  @DynamicPropertySource
  static void social(DynamicPropertyRegistry registry) {
    registry.add("app.social.public-url", () -> PUBLIC_URL);
    registry.add("app.social.token-key", () -> "test-social-token-key-123");
    registry.add("app.social.meta-app-id", () -> "meta-app");
    registry.add("app.social.meta-app-secret", () -> "meta-secret");
    registry.add("app.social.meta-graph-url", () -> FAKE.url() + "/graph");
    registry.add("app.social.meta-dialog-url", () -> "https://facebook.test/dialog/oauth");
    registry.add("app.social.tiktok-client-key", () -> "tt-key");
    registry.add("app.social.tiktok-client-secret", () -> "tt-secret");
    registry.add("app.social.tiktok-api-url", () -> FAKE.url() + "/tiktok");
    registry.add("app.social.tiktok-auth-url", () -> "https://tiktok.test/auth/");
  }

  @Autowired private SocialPublisher publisher;
  @Autowired private MutableClock clock;
  @Autowired private JdbcClient jdbc;

  private String owner;

  @BeforeEach
  void linkEverything() throws Exception {
    FAKE.facebook = "ok";
    FAKE.instagramStatus = "FINISHED";
    owner = owner();
    link("FACEBOOK", "meta")
        .andExpect(header().string("Location", "/social?linked=facebook,instagram"));
    link("TIKTOK", "tiktok").andExpect(header().string("Location", "/social?linked=tiktok"));
    put(owner, "/api/social/accounts/whatsapp", Map.of("number", "0712 345 678"))
        .andExpect(status().isOk());
    publishDue(); // anything left over from other tests in this class
    FAKE.requests.clear();
  }

  /** Press "Link", then come back from the network's sign-in page with a code. */
  private ResultActions link(String network, String provider) throws Exception {
    String url =
        body(post(owner, "/api/social/accounts/" + network + "/link", Map.of())
                .andExpect(status().isOk()))
            .path("authorizeUrl")
            .asText();
    org.springframework.util.MultiValueMap<String, String> params =
        UriComponentsBuilder.fromUriString(url).build().getQueryParams();
    assertThat(params.getFirst("redirect_uri"))
        .isEqualTo(PUBLIC_URL + "/api/social/callback/" + provider);
    String state = params.getFirst("state");
    return mvc.perform(
            MockMvcRequestBuilders.get("/api/social/callback/" + provider)
                .param("code", "code-123")
                .param("state", state))
        .andExpect(status().isFound());
  }

  private void publishDue() {
    publisher.due().forEach(publisher::publishOne);
  }

  static byte[] picture(int width, int height, String format) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private ResultActions createPost(String token, byte[] image, String... networks)
      throws Exception {
    var request =
        MockMvcRequestBuilders.multipart("/api/social/posts")
            .file(new MockMultipartFile("image", "phone.png", "image/png", image))
            .param("title", "New Samsung A15 in stock!")
            .param("description", "KES 18,999 this week at Resma Plaza G-13.")
            .param("networks", networks)
            .param("whatsAppLink", "true")
            .header("Authorization", "Bearer " + token);
    return mvc.perform(request);
  }

  private JsonNode findPost(long id) throws Exception {
    for (JsonNode p : body(get(owner, "/api/social/posts?size=50")).path("content")) {
      if (p.path("id").asLong() == id) {
        return p;
      }
    }
    throw new AssertionError("No post " + id);
  }

  private static String targetStatus(JsonNode post, String network) {
    for (JsonNode t : post.path("targets")) {
      if (t.path("network").asText().equals(network)) {
        return t.path("status").asText();
      }
    }
    throw new AssertionError("No " + network + " in " + post);
  }

  @Test
  void linksAccountsAndPostsToAllOfThem() throws Exception {
    get(owner, "/api/social/accounts")
        .andExpect(jsonPath("$[?(@.network == 'FACEBOOK')].name").value("Tavuzi Gadgets"))
        .andExpect(jsonPath("$[?(@.network == 'INSTAGRAM')].name").value("@tavuzigadgets"))
        .andExpect(jsonPath("$[?(@.network == 'TIKTOK')].name").value("Tavuzi on TikTok"))
        .andExpect(jsonPath("$[?(@.network == 'WHATSAPP')].name").value("+254712345678"));
    // Tokens are stored encrypted, never as they came from Facebook.
    String saved =
        jdbc.sql("select access_token from social_accounts where network = 'FACEBOOK'")
            .query(String.class)
            .single();
    assertThat(saved).isNotBlank().doesNotContain("page-token-1");

    JsonNode created =
        body(
            createPost(
                    owner,
                    picture(1600, 1600, "png"),
                    "FACEBOOK",
                    "INSTAGRAM",
                    "TIKTOK",
                    "WHATSAPP")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targets.length()").value(3)));
    long id = created.path("id").asLong();
    assertThat(created.path("caption").asText())
        .startsWith("New Samsung A15 in stock!\n\nKES 18,999")
        .endsWith("Order on WhatsApp: https://wa.me/254712345678");

    // The picture is public (Instagram and TikTok fetch it) and was made a 1080 px JPEG.
    String imageUrl = created.path("imageUrl").asText();
    byte[] jpeg =
        mvc.perform(MockMvcRequestBuilders.get(imageUrl))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(jpeg)).getWidth()).isEqualTo(1080);

    // Instagram is still fetching the picture on the first run.
    FAKE.instagramStatus = "IN_PROGRESS";
    publishDue();
    JsonNode afterFirst = findPost(id);
    assertThat(targetStatus(afterFirst, "FACEBOOK")).isEqualTo("POSTED");
    assertThat(targetStatus(afterFirst, "INSTAGRAM")).isEqualTo("PENDING");
    assertThat(targetStatus(afterFirst, "TIKTOK")).isEqualTo("SENT_TO_INBOX");

    FakeSocial.Request facebook = FAKE.to("/111/photos").getFirst();
    assertThat(facebook.authorization()).isEqualTo("Bearer page-token-1");
    assertThat(facebook.body()).contains("New Samsung A15 in stock!");
    String publicImage = PUBLIC_URL + imageUrl;
    assertThat(java.net.URLDecoder.decode(FAKE.to("/999/media").getFirst().body(), "UTF-8"))
        .contains("image_url=" + publicImage);
    FakeSocial.Request tiktok = FAKE.to("/content/init/").getFirst();
    assertThat(tiktok.authorization()).isEqualTo("Bearer tt-access");
    assertThat(tiktok.body())
        .contains(
            "\"PULL_FROM_URL\"", "\"MEDIA_UPLOAD\"", publicImage, "New Samsung A15 in stock!");

    // Ready now: it is published on the next run after the wait.
    FAKE.instagramStatus = "FINISHED";
    clock.advance(SocialPublisher.INSTAGRAM_RECHECK.plusSeconds(1));
    owner = owner();
    publishDue();
    assertThat(targetStatus(findPost(id), "INSTAGRAM")).isEqualTo("POSTED");
    assertThat(FAKE.to("/999/media")).hasSize(1); // the picture was not sent twice
    assertThat(FAKE.to("/media_publish")).hasSize(1);
  }

  @Test
  void failuresAreShownAndCanBeSentAgain() throws Exception {
    // Facebook refuses: failed at once, with Facebook's reason.
    FAKE.facebook = "refuse";
    long id =
        body(createPost(owner, picture(800, 800, "png"), "FACEBOOK")
                .andExpect(status().isCreated()))
            .path("id")
            .asLong();
    publishDue();
    JsonNode failed = findPost(id).path("targets").get(0);
    assertThat(failed.path("status").asText()).isEqualTo("FAILED");
    assertThat(failed.path("error").asText()).contains("The Page token has expired");

    // Facebook down: kept and tried again later.
    FAKE.facebook = "down";
    post(owner, "/api/social/posts/" + id + "/retry/FACEBOOK", Map.of())
        .andExpect(status().isNoContent());
    publishDue();
    JsonNode waiting = findPost(id).path("targets").get(0);
    assertThat(waiting.path("status").asText()).isEqualTo("PENDING");
    assertThat(waiting.path("attempts").asInt()).isEqualTo(1);

    FAKE.facebook = "ok";
    clock.advance(Duration.ofMinutes(2));
    owner = owner();
    publishDue();
    assertThat(targetStatus(findPost(id), "FACEBOOK")).isEqualTo("POSTED");
    post(owner, "/api/social/posts/" + id + "/retry/FACEBOOK", Map.of())
        .andExpect(status().isConflict());
  }

  @Test
  void checksPostsAndPermissions() throws Exception {
    String manager = newUser("MANAGER");
    String cashier = newUser("CASHIER");
    // Managers post but can't link accounts; cashiers can do neither.
    createPost(manager, picture(800, 800, "png"), "FACEBOOK").andExpect(status().isCreated());
    post(manager, "/api/social/accounts/FACEBOOK/link", Map.of()).andExpect(status().isForbidden());
    createPost(cashier, picture(800, 800, "png"), "FACEBOOK").andExpect(status().isForbidden());
    get(cashier, "/api/social/accounts").andExpect(status().isForbidden());

    // Instagram refuses very tall pictures, so the shop hears it before posting.
    createPost(owner, picture(500, 1500, "png"), "INSTAGRAM")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Instagram")));
    createPost(owner, "not a picture".getBytes(), "FACEBOOK")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Use a JPEG or PNG picture."));
    createPost(owner, picture(800, 800, "png")).andExpect(status().isBadRequest());

    // Unlinked networks can't be chosen.
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/social/accounts/TIKTOK")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isNoContent());
    createPost(owner, picture(800, 800, "png"), "TIKTOK")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Link TikTok first."));

    // A made-up or reused link request is refused.
    mvc.perform(
            MockMvcRequestBuilders.get("/api/social/callback/meta")
                .param("code", "x")
                .param("state", "forged"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/social?error=")));
    mvc.perform(MockMvcRequestBuilders.get("/api/social/images/nope.jpg"))
        .andExpect(status().isNotFound());
  }
}
