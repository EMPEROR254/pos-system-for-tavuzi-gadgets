package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.PostSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.PostView;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.TargetView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating posts and listing them. A post is saved with one "target" per chosen network and sent in
 * the background (SocialPublisher), so the screen answers at once even when a network is slow.
 */
@Service
public class SocialPostService {

  static final int TITLE_MAX = 150;
  static final int DESCRIPTION_MAX = 2000;

  private final SocialPostRepository posts;
  private final SocialPostTargetRepository targets;
  private final SocialAccountRepository accounts;
  private final SocialLinkService links;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public SocialPostService(
      SocialPostRepository posts,
      SocialPostTargetRepository targets,
      SocialAccountRepository accounts,
      SocialLinkService links,
      Clock clock) {
    this.posts = posts;
    this.targets = targets;
    this.accounts = accounts;
    this.links = links;
    this.clock = clock;
  }

  public record NewPost(
      String title,
      String description,
      Set<SocialNetwork> networks,
      boolean whatsAppLink,
      byte[] image) {}

  @Transactional
  public PostView create(NewPost p, AuthUser me) {
    String title = p.title() == null ? "" : p.title().trim();
    String description =
        p.description() == null || p.description().isBlank() ? null : p.description().trim();
    if (title.isEmpty()) {
      throw ApiException.badRequest("Write the post.");
    }
    if (title.length() > TITLE_MAX) {
      throw ApiException.badRequest(
          "Keep the post to " + TITLE_MAX + " characters; use the description for more.");
    }
    if (description != null && description.length() > DESCRIPTION_MAX) {
      throw ApiException.badRequest("Keep the description to " + DESCRIPTION_MAX + " characters.");
    }
    if (p.image() == null || p.image().length == 0) {
      throw ApiException.badRequest("Add a picture.");
    }
    Set<SocialNetwork> chosen = p.networks() == null ? Set.of() : p.networks();
    Set<SocialNetwork> server = EnumSet.noneOf(SocialNetwork.class);
    chosen.stream().filter(SocialNetwork::postedByServer).forEach(server::add);
    if (chosen.isEmpty()) {
      throw ApiException.badRequest("Choose where to post.");
    }
    for (SocialNetwork n : server) {
      if (!accounts.existsById(n)) {
        throw ApiException.badRequest("Link " + label(n) + " first.");
      }
    }
    // Sharing to WhatsApp happens on the phone; the number is only needed for the chat link.
    String whatsApp = null;
    if (p.whatsAppLink()) {
      whatsApp =
          links
              .whatsAppNumber()
              .orElseThrow(
                  () -> ApiException.badRequest("Add the shop's WhatsApp Business number first."));
    }

    ImageProcessor.Prepared image = ImageProcessor.prepare(p.image());
    server.forEach(n -> ImageProcessor.checkFits(image, n));
    Instant now = clock.instant();
    SocialPost post =
        posts.save(
            new SocialPost(
                title,
                description,
                Captions.caption(title, description, whatsApp),
                image,
                HexFormat.of().formatHex(randomBytes()),
                me.id(),
                now));
    List<SocialPostTarget> saved =
        server.stream().map(n -> targets.save(new SocialPostTarget(post.getId(), n, now))).toList();
    PostSummary summary =
        new PostSummary(
            post.getId(), title, description, post.getCaption(), post.getImageToken(), null, now);
    return view(summary, saved);
  }

  @Transactional(readOnly = true)
  public Page<PostView> list(Pageable pageable) {
    Page<PostSummary> page = posts.recent(pageable);
    // All targets of the page in one query (no query per post).
    Map<Long, List<SocialPostTarget>> byPost =
        targets.findByPostIdInOrderByNetwork(page.map(PostSummary::id).getContent()).stream()
            .collect(Collectors.groupingBy(SocialPostTarget::getPostId));
    return page.map(s -> view(s, byPost.getOrDefault(s.id(), List.of())));
  }

  /** Try a failed network again. */
  @Transactional
  public void retry(long postId, SocialNetwork network) {
    SocialPostTarget t =
        targets
            .findByPostIdAndNetwork(postId, network)
            .orElseThrow(
                () -> ApiException.notFound("That post was not sent to " + label(network) + "."));
    if (t.getStatus() != SocialPostTarget.Status.FAILED) {
      throw ApiException.conflict("Only failed posts can be sent again.");
    }
    if (!accounts.existsById(network)) {
      throw ApiException.badRequest("Link " + label(network) + " first.");
    }
    t.retry(clock.instant());
  }

  @Transactional(readOnly = true)
  public Optional<byte[]> image(String token) {
    return posts.imageByToken(token);
  }

  static PostView view(PostSummary s, List<SocialPostTarget> list) {
    return new PostView(
        s.id(),
        s.title(),
        s.description(),
        s.caption(),
        "/api/social/images/" + s.imageToken() + ".jpg",
        s.createdByName(),
        s.createdAt(),
        list.stream()
            .map(t -> new TargetView(t.getNetwork(), t.getStatus(), t.getError(), t.getAttempts()))
            .toList());
  }

  static String label(SocialNetwork n) {
    return switch (n) {
      case FACEBOOK -> "Facebook";
      case INSTAGRAM -> "Instagram";
      case TIKTOK -> "TikTok";
      case WHATSAPP -> "WhatsApp";
    };
  }

  private byte[] randomBytes() {
    byte[] b = new byte[24];
    random.nextBytes(b);
    return b;
  }
}
