package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.AccountView;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.LinkStart;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.PostView;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.WhatsAppRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * /api/social: linking accounts (owner - MANAGE_SETTINGS) and posting (owner and manager -
 * SEND_PROMOTIONS).
 */
@RestController
@RequestMapping("/api/social")
public class SocialController {

  private final SocialLinkService links;
  private final SocialPostService posts;

  public SocialController(SocialLinkService links, SocialPostService posts) {
    this.links = links;
    this.posts = posts;
  }

  @GetMapping("/accounts")
  @PreAuthorize("hasAnyAuthority('SEND_PROMOTIONS', 'MANAGE_SETTINGS')")
  public List<AccountView> accounts() {
    return links.accounts();
  }

  /** Returns the sign-in page to send the owner to (Facebook covers Instagram too). */
  @PostMapping("/accounts/{network}/link")
  @PreAuthorize("hasAuthority('MANAGE_SETTINGS')")
  public LinkStart link(@AuthenticationPrincipal AuthUser me, @PathVariable SocialNetwork network) {
    return new LinkStart(links.startLink(network, me));
  }

  @PutMapping("/accounts/whatsapp")
  @PreAuthorize("hasAuthority('MANAGE_SETTINGS')")
  public AccountView whatsApp(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody WhatsAppRequest request) {
    return links.setWhatsApp(request.number(), me);
  }

  @DeleteMapping("/accounts/{network}")
  @PreAuthorize("hasAuthority('MANAGE_SETTINGS')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(@PathVariable SocialNetwork network) {
    links.unlink(network);
  }

  /** multipart/form-data: title, description, networks (repeated), whatsAppLink, image. */
  @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('SEND_PROMOTIONS')")
  @ResponseStatus(HttpStatus.CREATED)
  public PostView create(
      @AuthenticationPrincipal AuthUser me,
      @RequestParam String title,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) Set<SocialNetwork> networks,
      @RequestParam(defaultValue = "false") boolean whatsAppLink,
      @RequestPart(required = false) MultipartFile image) {
    try {
      byte[] bytes = image == null ? null : image.getBytes();
      return posts.create(
          new SocialPostService.NewPost(title, description, networks, whatsAppLink, bytes), me);
    } catch (IOException e) {
      throw ApiException.badRequest("The picture could not be read. Try again.");
    }
  }

  @GetMapping("/posts")
  @PreAuthorize("hasAuthority('SEND_PROMOTIONS')")
  public PageResponse<PostView> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    return PageResponse.of(posts.list(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50))));
  }

  @PostMapping("/posts/{id}/retry/{network}")
  @PreAuthorize("hasAuthority('SEND_PROMOTIONS')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retry(@PathVariable long id, @PathVariable SocialNetwork network) {
    posts.retry(id, network);
  }
}
