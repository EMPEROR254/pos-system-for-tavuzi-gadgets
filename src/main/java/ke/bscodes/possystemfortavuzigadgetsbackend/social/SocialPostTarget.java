package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * One network a post goes to. Sent in the background: a network being down is retried after 1 and
 * then 5 minutes; a clear refusal (e.g. picture not allowed) fails at once with the reason.
 */
@Entity
@Table(name = "social_post_targets")
public class SocialPostTarget {

  public enum Status {
    PENDING,
    POSTED,
    /** TikTok: in the shop's TikTok inbox, to be finished (sound, publish) in the TikTok app. */
    SENT_TO_INBOX,
    FAILED
  }

  static final int MAX_ATTEMPTS = 3;
  private static final Duration[] RETRY_AFTER = {Duration.ofMinutes(1), Duration.ofMinutes(5)};

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long postId;

  @Enumerated(EnumType.STRING)
  private SocialNetwork network;

  @Enumerated(EnumType.STRING)
  private Status status = Status.PENDING;

  private int attempts;
  private Instant nextAttemptAt;
  private String containerId;
  private String externalId;
  private String error;
  private Instant postedAt;

  protected SocialPostTarget() {}

  public SocialPostTarget(long postId, SocialNetwork network, Instant now) {
    this.postId = postId;
    this.network = network;
    this.nextAttemptAt = now;
  }

  public void done(Status status, String externalId, Instant now) {
    this.status = status;
    this.externalId = externalId;
    this.error = null;
    this.postedAt = now;
  }

  /** Instagram prepares the picture first; check again shortly without counting a failure. */
  public void waitForContainer(String containerId, Instant next) {
    this.containerId = containerId;
    this.nextAttemptAt = next;
  }

  public void failed(String reason, boolean tryAgain, Instant now) {
    attempts++;
    error = reason == null ? "Failed" : reason.substring(0, Math.min(300, reason.length()));
    containerId = null;
    if (tryAgain && attempts < MAX_ATTEMPTS) {
      nextAttemptAt = now.plus(RETRY_AFTER[Math.min(attempts - 1, RETRY_AFTER.length - 1)]);
    } else {
      status = Status.FAILED;
    }
  }

  /** The owner asks to try a failed network again. */
  public void retry(Instant now) {
    status = Status.PENDING;
    attempts = 0;
    error = null;
    containerId = null;
    nextAttemptAt = now;
  }

  public Long getId() {
    return id;
  }

  public Long getPostId() {
    return postId;
  }

  public SocialNetwork getNetwork() {
    return network;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getContainerId() {
    return containerId;
  }

  public String getError() {
    return error;
  }
}
