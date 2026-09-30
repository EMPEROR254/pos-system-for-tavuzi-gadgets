package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TikTok Content Posting API. Photos are sent to the account's TikTok inbox (post mode
 * MEDIA_UPLOAD): the owner adds a sound and publishes in the TikTok app, which TikTok requires
 * until an app passes its audit. TikTok fetches the picture from its public link.
 */
@Component
public class TikTokClient {

  public record Tokens(
      String openId,
      String accessToken,
      long expiresIn,
      String refreshToken,
      long refreshExpiresIn) {}

  static final String SCOPES = "user.info.basic,video.upload";

  private final RestClient http;
  private final SocialProperties props;

  public TikTokClient(RestClient.Builder builder, SocialProperties props) {
    this.http = builder.clone().build();
    this.props = props;
  }

  public String authorizeUrl(String state) {
    return UriComponentsBuilder.fromUriString(props.tiktokAuthUrl())
        .queryParam("client_key", props.tiktokClientKey())
        .queryParam("scope", SCOPES)
        .queryParam("response_type", "code")
        .queryParam("redirect_uri", props.redirectUri("tiktok"))
        .queryParam("state", state)
        .encode()
        .toUriString();
  }

  public Tokens exchange(String code) {
    MultiValueMap<String, String> form = form();
    form.add("code", code);
    form.add("grant_type", "authorization_code");
    form.add("redirect_uri", props.redirectUri("tiktok"));
    return tokens(form);
  }

  /** Access tokens last a day; the refresh token (a year) gets a new one. */
  public Tokens refresh(String refreshToken) {
    MultiValueMap<String, String> form = form();
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);
    return tokens(form);
  }

  public String displayName(String accessToken) {
    JsonNode reply =
        check(
            SocialHttp.send(
                http.get()
                    .uri(props.tiktokApiUrl() + "/v2/user/info/?fields=open_id,display_name")
                    .headers(h -> h.setBearerAuth(accessToken)),
                "TikTok"));
    return reply.path("data").path("user").path("display_name").asText("TikTok account");
  }

  /** Sends the photo post to the TikTok inbox. Returns TikTok's publish id. */
  public String sendPhoto(String accessToken, String title, String description, String imageUrl) {
    Map<String, Object> body =
        Map.of(
            "post_info",
            Map.of("title", title, "description", description),
            "source_info",
            Map.of(
                "source",
                "PULL_FROM_URL",
                "photo_cover_index",
                0,
                "photo_images",
                List.of(imageUrl)),
            "post_mode",
            "MEDIA_UPLOAD",
            "media_type",
            "PHOTO");
    JsonNode reply =
        check(
            SocialHttp.send(
                http.post()
                    .uri(props.tiktokApiUrl() + "/v2/post/publish/content/init/")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body),
                "TikTok"));
    return reply.path("data").path("publish_id").asText();
  }

  private Tokens tokens(MultiValueMap<String, String> form) {
    JsonNode t =
        SocialHttp.send(
            http.post()
                .uri(props.tiktokApiUrl() + "/v2/oauth/token/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form),
            "TikTok");
    if (!t.hasNonNull("access_token")) {
      throw new SocialApiException("TikTok: " + SocialHttp.message(t, 400), false);
    }
    return new Tokens(
        t.path("open_id").asText(),
        t.path("access_token").asText(),
        t.path("expires_in").asLong(),
        t.path("refresh_token").asText(),
        t.path("refresh_expires_in").asLong());
  }

  private MultiValueMap<String, String> form() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_key", props.tiktokClientKey());
    form.add("client_secret", props.tiktokClientSecret());
    return form;
  }

  /** TikTok answers 200 with {"error":{"code":"ok"}} on success, another code on failure. */
  private static JsonNode check(JsonNode reply) {
    String code = reply.path("error").path("code").asText("ok");
    if (!"ok".equals(code)) {
      boolean retry = code.contains("rate_limit") || code.contains("internal");
      throw new SocialApiException("TikTok: " + SocialHttp.message(reply, 400), retry);
    }
    return reply;
  }
}
