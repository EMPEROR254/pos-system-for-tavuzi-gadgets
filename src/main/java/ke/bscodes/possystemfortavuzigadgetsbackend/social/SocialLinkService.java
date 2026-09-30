package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerService;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.AccountView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linking the shop's accounts. Facebook/Instagram and TikTok use their own sign-in pages (OAuth):
 * the owner agrees there, and the network sends them back to our callback with a one-time code that
 * we swap for access tokens. WhatsApp only needs the business number.
 */
@Service
public class SocialLinkService {

  private static final Logger log = LoggerFactory.getLogger(SocialLinkService.class);
  static final Duration STATE_VALID_FOR = Duration.ofMinutes(15);

  private final SocialAccountRepository accounts;
  private final OAuthStateRepository states;
  private final MetaClient meta;
  private final TikTokClient tiktok;
  private final TokenCipher cipher;
  private final SocialProperties props;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public SocialLinkService(
      SocialAccountRepository accounts,
      OAuthStateRepository states,
      MetaClient meta,
      TikTokClient tiktok,
      TokenCipher cipher,
      SocialProperties props,
      Clock clock) {
    this.accounts = accounts;
    this.states = states;
    this.meta = meta;
    this.tiktok = tiktok;
    this.cipher = cipher;
    this.props = props;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AccountView> accounts() {
    return List.of(SocialNetwork.values()).stream()
        .map(
            n -> {
              Optional<SocialAccount> a = accounts.findById(n);
              String setup = setupProblem(n);
              return new AccountView(
                  n,
                  a.isPresent(),
                  a.map(SocialAccount::getName).orElse(null),
                  a.map(SocialAccount::getLinkedAt).orElse(null),
                  setup == null,
                  setup);
            })
        .sorted(Comparator.comparing(AccountView::network))
        .toList();
  }

  /** What still has to be set up before this network can be linked (null = ready). */
  String setupProblem(SocialNetwork network) {
    if (network == SocialNetwork.WHATSAPP) {
      return null;
    }
    boolean keys =
        network == SocialNetwork.TIKTOK ? props.tiktokConfigured() : props.metaConfigured();
    if (!keys) {
      return network == SocialNetwork.TIKTOK
          ? "Set TIKTOK_CLIENT_KEY and TIKTOK_CLIENT_SECRET on the server."
          : "Set META_APP_ID and META_APP_SECRET on the server.";
    }
    if (!props.hasPublicUrl()) {
      return "Set APP_PUBLIC_URL (the shop's public https address) on the server.";
    }
    if (!cipher.ready()) {
      return "Set SOCIAL_TOKEN_KEY (at least 16 characters) on the server.";
    }
    return null;
  }

  /** Starts linking: the web app sends the owner to the returned sign-in page. */
  @Transactional
  public String startLink(SocialNetwork network, AuthUser me) {
    String problem = setupProblem(network);
    if (network == SocialNetwork.WHATSAPP) {
      throw ApiException.badRequest("WhatsApp is linked by typing the business number.");
    }
    if (problem != null) {
      throw ApiException.conflict(problem);
    }
    String provider = network == SocialNetwork.TIKTOK ? "tiktok" : "meta";
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    states.save(new OAuthState(state, provider.toUpperCase(), me.id(), clock.instant()));
    return provider.equals("tiktok") ? tiktok.authorizeUrl(state) : meta.authorizeUrl(state);
  }

  /**
   * The network sent the owner back. Returns what to show on the Social screen: the linked network
   * or a short error. The state is used once and must be recent.
   */
  @Transactional
  public String finishLink(String provider, String code, String state, String error) {
    Optional<OAuthState> saved = state == null ? Optional.empty() : states.findById(state);
    saved.ifPresent(states::delete);
    Instant now = clock.instant();
    if (saved.isEmpty()
        || !saved.get().getProvider().equalsIgnoreCase(provider)
        || saved.get().getCreatedAt().isBefore(now.minus(STATE_VALID_FOR))) {
      throw ApiException.badRequest("That link request has expired. Press Link again.");
    }
    if (error != null || code == null || code.isBlank()) {
      throw ApiException.badRequest("Linking was cancelled.");
    }
    long userId = saved.get().getUserId();
    return provider.equalsIgnoreCase("tiktok")
        ? linkTikTok(code, userId, now)
        : linkMeta(code, userId, now);
  }

  private String linkMeta(String code, long userId, Instant now) {
    List<MetaClient.Page> pages = meta.pages(code);
    if (pages.isEmpty()) {
      throw ApiException.badRequest(
          "No Facebook Page was shared. Link again and tick the shop's Page.");
    }
    // The shop's Page is the one with Instagram connected, if any; otherwise the first one shared.
    MetaClient.Page page =
        pages.stream().filter(p -> p.instagramId() != null).findFirst().orElse(pages.get(0));
    String token = cipher.encrypt(page.token());
    save(SocialNetwork.FACEBOOK, page.id(), page.name(), token, null, null, userId, now);
    if (page.instagramId() != null) {
      String name = page.instagramName() == null ? page.name() : "@" + page.instagramName();
      save(SocialNetwork.INSTAGRAM, page.instagramId(), name, token, null, null, userId, now);
      return "facebook,instagram";
    }
    log.info("Facebook Page linked without an Instagram business account");
    return "facebook";
  }

  private String linkTikTok(String code, long userId, Instant now) {
    TikTokClient.Tokens t = tiktok.exchange(code);
    save(
        SocialNetwork.TIKTOK,
        t.openId(),
        tiktok.displayName(t.accessToken()),
        cipher.encrypt(t.accessToken()),
        cipher.encrypt(t.refreshToken()),
        now.plusSeconds(t.expiresIn()),
        userId,
        now);
    return "tiktok";
  }

  private void save(
      SocialNetwork network,
      String externalId,
      String name,
      String token,
      String refresh,
      Instant expires,
      long userId,
      Instant now) {
    SocialAccount account = accounts.findById(network).orElseGet(() -> new SocialAccount(network));
    account.link(externalId, name, token, refresh, expires, userId, now);
    accounts.save(account);
  }

  @Transactional
  public void unlink(SocialNetwork network) {
    accounts.deleteById(network);
  }

  /** WhatsApp: the shop's business number, for "Order on WhatsApp" links in posts. */
  @Transactional
  public AccountView setWhatsApp(String number, AuthUser me) {
    String phone = CustomerService.phone(number);
    save(SocialNetwork.WHATSAPP, phone, "+" + phone, null, null, null, me.id(), clock.instant());
    return accounts().stream()
        .filter(a -> a.network() == SocialNetwork.WHATSAPP)
        .findFirst()
        .orElseThrow();
  }

  @Transactional(readOnly = true)
  public Optional<String> whatsAppNumber() {
    return accounts.findById(SocialNetwork.WHATSAPP).map(SocialAccount::getExternalId);
  }
}
