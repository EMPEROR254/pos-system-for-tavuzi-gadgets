package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Facebook Graph API: link a Facebook Page (and the Instagram business account connected to it),
 * post a photo to the Page, and post to Instagram (make a media container from the picture's public
 * link, wait until Instagram has fetched it, then publish).
 */
@Component
public class MetaClient {

  /** A Page the owner manages, with its Instagram business account if one is connected. */
  public record Page(
      String id, String name, String token, String instagramId, String instagramName) {}

  static final String SCOPES =
      "pages_show_list,pages_read_engagement,pages_manage_posts,business_management,"
          + "instagram_basic,instagram_content_publish";

  private final RestClient http;
  private final SocialProperties props;

  public MetaClient(RestClient.Builder builder, SocialProperties props) {
    this.http = builder.clone().build();
    this.props = props;
  }

  public String authorizeUrl(String state) {
    return UriComponentsBuilder.fromUriString(props.metaDialogUrl())
        .queryParam("client_id", props.metaAppId())
        .queryParam("redirect_uri", props.redirectUri("meta"))
        .queryParam("state", state)
        .queryParam("response_type", "code")
        .queryParam("scope", SCOPES)
        .encode()
        .toUriString();
  }

  /**
   * Swaps the code for a long-lived user token, then lists the Pages. Page tokens got from a
   * long-lived user token don't expire, so the shop doesn't have to link again every two months.
   */
  public java.util.List<Page> pages(String code) {
    String shortToken =
        get(graph("/oauth/access_token")
                .queryParam("client_id", props.metaAppId())
                .queryParam("client_secret", props.metaAppSecret())
                .queryParam("redirect_uri", props.redirectUri("meta"))
                .queryParam("code", code))
            .path("access_token")
            .asText();
    String longToken =
        get(graph("/oauth/access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", props.metaAppId())
                .queryParam("client_secret", props.metaAppSecret())
                .queryParam("fb_exchange_token", shortToken))
            .path("access_token")
            .asText();
    JsonNode data =
        SocialHttp.send(
                http.get()
                    .uri(
                        graph("/me/accounts")
                            .queryParam(
                                "fields",
                                "id,name,access_token,instagram_business_account{id,username}")
                            .encode()
                            .toUriString())
                    .headers(h -> h.setBearerAuth(longToken)),
                "Facebook")
            .path("data");
    java.util.List<Page> pages = new java.util.ArrayList<>();
    for (JsonNode p : data) {
      JsonNode ig = p.path("instagram_business_account");
      pages.add(
          new Page(
              p.path("id").asText(),
              p.path("name").asText(),
              p.path("access_token").asText(),
              ig.hasNonNull("id") ? ig.path("id").asText() : null,
              ig.hasNonNull("username") ? ig.path("username").asText() : null));
    }
    return pages;
  }

  /** Posts the picture and caption to the Page. Returns Facebook's post id. */
  public String postPhoto(String pageId, String pageToken, byte[] jpeg, String caption) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "source",
        new ByteArrayResource(jpeg) {
          @Override
          public String getFilename() {
            return "post.jpg";
          }
        });
    form.add("message", caption);
    JsonNode reply =
        SocialHttp.send(
            http.post()
                .uri(props.metaGraphUrl() + "/" + pageId + "/photos")
                .headers(h -> h.setBearerAuth(pageToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form),
            "Facebook");
    return reply.hasNonNull("post_id") ? reply.path("post_id").asText() : reply.path("id").asText();
  }

  /** Step 1 for Instagram: Instagram fetches the picture from its public link. */
  public String instagramContainer(String igId, String pageToken, String imageUrl, String caption) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("image_url", imageUrl);
    form.add("caption", caption);
    return postForm("/" + igId + "/media", pageToken, form).path("id").asText();
  }

  /** FINISHED when the picture is ready; ERROR if Instagram could not use it. */
  public String instagramStatus(String containerId, String pageToken) {
    return SocialHttp.send(
            http.get()
                .uri(
                    graph("/" + containerId)
                        .queryParam("fields", "status_code")
                        .encode()
                        .toUriString())
                .headers(h -> h.setBearerAuth(pageToken)),
            "Instagram")
        .path("status_code")
        .asText();
  }

  /** Step 2: publish the prepared container. Returns Instagram's media id. */
  public String instagramPublish(String igId, String pageToken, String containerId) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("creation_id", containerId);
    return postForm("/" + igId + "/media_publish", pageToken, form).path("id").asText();
  }

  private JsonNode postForm(String path, String token, MultiValueMap<String, String> form) {
    return SocialHttp.send(
        http.post()
            .uri(props.metaGraphUrl() + path)
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form),
        "Instagram");
  }

  private UriComponentsBuilder graph(String path) {
    return UriComponentsBuilder.fromUriString(props.metaGraphUrl() + path);
  }

  private JsonNode get(UriComponentsBuilder uri) {
    return SocialHttp.send(http.get().uri(uri.encode().toUriString()), "Facebook");
  }
}
