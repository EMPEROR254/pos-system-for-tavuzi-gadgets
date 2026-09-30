package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public parts of social media (no sign-in, see SecurityConfig): the page Facebook and
 * TikTok send the owner back to after linking, and post pictures, which Instagram and TikTok fetch
 * by their random link.
 */
@RestController
@RequestMapping("/api/social")
public class SocialPublicController {

  private final SocialLinkService links;
  private final SocialPostService posts;

  public SocialPublicController(SocialLinkService links, SocialPostService posts) {
    this.links = links;
    this.posts = posts;
  }

  /** Finishes linking, then sends the browser back to the Social screen with the result. */
  @GetMapping("/callback/{provider}")
  public ResponseEntity<Void> callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    String target;
    try {
      target = "/social?linked=" + links.finishLink(provider, code, state, error);
    } catch (ApiException | SocialApiException e) {
      target = "/social?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
    }
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
  }

  @GetMapping("/images/{file}")
  public ResponseEntity<byte[]> image(@PathVariable String file) {
    String token = file.endsWith(".jpg") ? file.substring(0, file.length() - 4) : file;
    return posts
        .image(token)
        .map(
            bytes ->
                ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                    .body(bytes))
        .orElseThrow(() -> ApiException.notFound("No such picture."));
  }
}
