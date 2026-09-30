package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends posts to the networks, one target at a time, each in its own transaction so one network's
 * trouble never holds up the others. Tokens are decrypted only here, just before use.
 */
@Service
public class SocialPublisher {

  private static final Logger log = LoggerFactory.getLogger(SocialPublisher.class);
  private static final int BATCH = 20;

  /** Instagram usually has the picture ready within seconds; look again after this. */
  static final Duration INSTAGRAM_RECHECK = Duration.ofSeconds(30);

  /** Refresh TikTok's day-long token when it is this close to running out. */
  static final Duration TIKTOK_REFRESH_BEFORE = Duration.ofMinutes(10);

  private final SocialPostTargetRepository targets;
  private final SocialPostRepository posts;
  private final SocialAccountRepository accounts;
  private final MetaClient meta;
  private final TikTokClient tiktok;
  private final TokenCipher cipher;
  private final SocialProperties props;
  private final Clock clock;

  public SocialPublisher(
      SocialPostTargetRepository targets,
      SocialPostRepository posts,
      SocialAccountRepository accounts,
      MetaClient meta,
      TikTokClient tiktok,
      TokenCipher cipher,
      SocialProperties props,
      Clock clock) {
    this.targets = targets;
    this.posts = posts;
    this.accounts = accounts;
    this.meta = meta;
    this.tiktok = tiktok;
    this.cipher = cipher;
    this.props = props;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Long> due() {
    return targets.dueIds(clock.instant(), PageRequest.of(0, BATCH));
  }

  @Transactional
  public void publishOne(long targetId) {
    SocialPostTarget t = targets.findById(targetId).orElse(null);
    if (t == null || t.getStatus() != SocialPostTarget.Status.PENDING) {
      return;
    }
    Instant now = clock.instant();
    SocialAccount account = accounts.findById(t.getNetwork()).orElse(null);
    if (account == null) {
      t.failed(SocialPostService.label(t.getNetwork()) + " is no longer linked.", false, now);
      return;
    }
    SocialPost post = posts.findById(t.getPostId()).orElseThrow();
    try {
      switch (t.getNetwork()) {
        case FACEBOOK -> {
          String id =
              meta.postPhoto(
                  account.getExternalId(),
                  cipher.decrypt(account.getAccessToken()),
                  post.getImage(),
                  post.getCaption());
          t.done(SocialPostTarget.Status.POSTED, id, now);
        }
        case INSTAGRAM -> instagram(t, post, account, now);
        case TIKTOK -> {
          String id =
              tiktok.sendPhoto(
                  tiktokToken(account, now),
                  Captions.tiktokTitle(post.getTitle()),
                  post.getCaption(),
                  props.imageUrl(post.getImageToken()));
          t.done(SocialPostTarget.Status.SENT_TO_INBOX, id, now);
        }
        case WHATSAPP -> t.failed("WhatsApp posts are shared from the phone.", false, now);
      }
    } catch (SocialApiException e) {
      log.warn("Post {} to {} failed: {}", post.getId(), t.getNetwork(), e.getMessage());
      t.failed(e.getMessage(), e.retryable(), now);
    } catch (IllegalStateException e) {
      // The saved token can't be decrypted (e.g. SOCIAL_TOKEN_KEY changed): link again.
      t.failed(e.getMessage(), false, now);
    }
  }

  private void instagram(SocialPostTarget t, SocialPost post, SocialAccount account, Instant now) {
    String token = cipher.decrypt(account.getAccessToken());
    String container = t.getContainerId();
    if (container == null) {
      container =
          meta.instagramContainer(
              account.getExternalId(),
              token,
              props.imageUrl(post.getImageToken()),
              post.getCaption());
    }
    String status = meta.instagramStatus(container, token);
    switch (status) {
      case "FINISHED" ->
          t.done(
              SocialPostTarget.Status.POSTED,
              meta.instagramPublish(account.getExternalId(), token, container),
              now);
      case "ERROR", "EXPIRED" ->
          t.failed("Instagram could not use the picture (" + status + ").", false, now);
      default -> t.waitForContainer(container, now.plus(INSTAGRAM_RECHECK));
    }
  }

  private String tiktokToken(SocialAccount account, Instant now) {
    Instant expires = account.getTokenExpiresAt();
    if (expires != null && expires.isAfter(now.plus(TIKTOK_REFRESH_BEFORE))) {
      return cipher.decrypt(account.getAccessToken());
    }
    TikTokClient.Tokens fresh = tiktok.refresh(cipher.decrypt(account.getRefreshToken()));
    account.refreshed(
        cipher.encrypt(fresh.accessToken()),
        cipher.encrypt(fresh.refreshToken()),
        now.plusSeconds(fresh.expiresIn()));
    return fresh.accessToken();
  }
}
