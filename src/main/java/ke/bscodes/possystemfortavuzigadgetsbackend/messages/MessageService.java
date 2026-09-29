package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.text.DecimalFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.Customer;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerService;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignPreview;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignView;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.MessageView;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.ReplyView;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.SmsAccount;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ShopProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMS receipts and promotions. Everything goes into the outbox (sms_messages) first; SmsDispatcher
 * sends in the background (queued() then sendOne() per message), so a slow SMS provider never holds
 * up the till.
 */
@Service
public class MessageService {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Africa/Nairobi"));
  private static final int BATCH = 50;
  private static final int MAX_SCHEDULE_DAYS = 30;

  private final SmsMessageRepository messages;
  private final SmsCampaignRepository campaigns;
  private final SmsInboundRepository replies;
  private final CustomerRepository customerRepository;
  private final CustomerService customers;
  private final SmsSender sender;
  private final SmsProperties props;
  private final ShopProperties shop;
  private final Clock clock;

  public MessageService(
      SmsMessageRepository messages,
      SmsCampaignRepository campaigns,
      SmsInboundRepository replies,
      CustomerRepository customerRepository,
      CustomerService customers,
      SmsSender sender,
      SmsProperties props,
      ShopProperties shop,
      Clock clock) {
    this.messages = messages;
    this.campaigns = campaigns;
    this.replies = replies;
    this.customerRepository = customerRepository;
    this.customers = customers;
    this.sender = sender;
    this.props = props;
    this.shop = shop;
    this.clock = clock;
  }

  /** Queues the SMS receipt for a sale (no consent needed - it is the customer's own receipt). */
  @Transactional
  public void queueReceipt(SaleView sale, Long customerId) {
    if (sale.customerPhone() == null) {
      throw ApiException.badRequest("Add the customer's phone number to send an SMS receipt.");
    }
    messages.save(
        new SmsMessage(
            sale.customerPhone(),
            receiptText(sale),
            SmsMessage.Kind.RECEIPT,
            customerId,
            sale.id(),
            null,
            clock.instant()));
  }

  /** Short receipt: fits one or two SMS. */
  static String receiptText(SaleView sale, String shopName) {
    String paid =
        sale.payments().stream()
            .map(p -> p.label() + (p.reference() == null ? "" : " " + p.reference()))
            .collect(Collectors.joining(", "));
    StringBuilder text =
        new StringBuilder()
            .append(shopName)
            .append(" receipt ")
            .append(sale.receiptNo())
            .append(": KES ")
            .append(new DecimalFormat("#,##0.##").format(sale.total()))
            .append(" paid by ")
            .append(paid)
            .append('.');
    sale.lines().stream()
        .filter(l -> l.warrantyEndsAt() != null)
        .forEach(
            l ->
                text.append(' ')
                    .append(l.productName())
                    .append(" warranty to ")
                    .append(DATE.format(l.warrantyEndsAt()))
                    .append('.'));
    return text.append(" Thank you!").toString();
  }

  private String receiptText(SaleView sale) {
    return receiptText(sale, shop.name());
  }

  @Transactional(readOnly = true)
  public CampaignPreview preview(CampaignRequest r) {
    String full = promoText(r.message());
    return new CampaignPreview(audience(r).size(), full, SmsText.parts(full));
  }

  /**
   * Queues a promotion to every opted-in customer in the audience, now or at sendAt; never in the
   * quiet hours (it waits for the morning).
   */
  @Transactional
  public CampaignResult sendCampaign(CampaignRequest r, long userId) {
    Instant now = clock.instant();
    if (r.sendAt() != null && r.sendAt().isAfter(now.plus(Duration.ofDays(MAX_SCHEDULE_DAYS)))) {
      throw ApiException.badRequest("Schedule it within " + MAX_SCHEDULE_DAYS + " days.");
    }
    List<Customer> audience = audience(r);
    if (audience.isEmpty()) {
      throw ApiException.badRequest("No customers who agreed to promotions match this audience.");
    }
    Instant requested = r.sendAt() == null || r.sendAt().isBefore(now) ? now : r.sendAt();
    Instant sendsAt = props.quietHours().nextAllowed(requested);
    String full = promoText(r.message());
    SmsCampaign campaign =
        campaigns.save(new SmsCampaign(full, describe(r), audience.size(), userId, now));
    campaign.setSendAt(r.sendAt());
    for (Customer c : audience) {
      SmsMessage m =
          new SmsMessage(
              c.getPhone(), full, SmsMessage.Kind.PROMO, c.getId(), null, campaign.getId(), now);
      if (sendsAt.isAfter(now)) {
        m.setSendAfter(sendsAt);
      }
      messages.save(m);
    }
    return new CampaignResult(campaign.getId(), audience.size(), SmsText.parts(full), sendsAt);
  }

  @Transactional(readOnly = true)
  public List<CampaignView> campaigns() {
    return messages.campaigns(PageRequest.of(0, 20));
  }

  /** The network's delivery report for a message we sent. Unknown ids are ignored. */
  @Transactional
  public boolean recordDelivery(String providerMessageId, String status, String failureReason) {
    return messages
        .findFirstByProviderMessageId(providerMessageId)
        .map(
            m -> {
              m.recordDelivery(status, failureReason, clock.instant());
              return true;
            })
        .orElse(false);
  }

  /**
   * A reply to the shop's SMS number. "STOP" (or UNSUBSCRIBE) takes the number off promotions for
   * good. Each reply is stored once, even if the provider sends it twice.
   */
  @Transactional
  public boolean receiveReply(String providerId, String rawPhone, String text) {
    if (providerId != null && replies.existsByProviderId(providerId)) {
      return false;
    }
    String phone = CustomerService.phone(rawPhone);
    String body = text == null ? "" : text.trim();
    boolean stop = isStop(body);
    if (stop) {
      customers.optOut(phone, "REPLY_STOP");
    }
    replies.save(
        new SmsInbound(
            providerId,
            phone,
            body.substring(0, Math.min(500, body.length())),
            stop,
            clock.instant()));
    return stop;
  }

  /** The network says this number opted out (e.g. *456*9*5# on Safaricom). */
  @Transactional
  public void networkOptOut(String rawPhone) {
    customers.optOut(CustomerService.phone(rawPhone), "NETWORK");
  }

  static boolean isStop(String text) {
    String first = text.trim().split("\\s+")[0].toUpperCase(Locale.ROOT);
    return first.equals("STOP") || first.equals("UNSUBSCRIBE");
  }

  @Transactional(readOnly = true)
  public Page<ReplyView> replies(Pageable pageable) {
    return replies.recent(pageable);
  }

  /** Send a failed message again. */
  @Transactional
  public void resend(long id) {
    SmsMessage m = messages.findById(id).orElseThrow(() -> ApiException.notFound("No such SMS."));
    if (!m.canResend()) {
      throw ApiException.conflict("Only SMS that failed or were not delivered can be sent again.");
    }
    m.resend();
  }

  public SmsAccount account() {
    QuietHours q = props.quietHours();
    return new SmsAccount(props.live(), sender.balance().orElse(null), q.start() + "-" + q.end());
  }

  @Transactional(readOnly = true)
  public Page<MessageView> list(
      SmsMessage.Kind kind,
      SmsMessage.Status status,
      String phone,
      Long campaignId,
      Pageable pageable) {
    String p = phone == null || phone.isBlank() ? null : CustomerService.phone(phone);
    return messages.search(kind, status, p, campaignId, pageable);
  }

  /** Queued message ids that may go now, oldest first, for the dispatcher. */
  @Transactional(readOnly = true)
  public List<Long> queued() {
    return messages.queuedIds(clock.instant(), PageRequest.of(0, BATCH));
  }

  /** Sends one queued message and records the result (its own transaction). */
  @Transactional
  public void sendOne(long id) {
    SmsMessage m = messages.findById(id).orElse(null);
    if (m == null || m.getStatus() != SmsMessage.Status.QUEUED) {
      return;
    }
    m.recordAttempt(sender.send(m.getPhone(), m.getBody()), clock.instant());
  }

  private List<Customer> audience(CampaignRequest r) {
    Instant since =
        r.boughtWithinDays() == null
            ? null
            : clock.instant().minus(Duration.ofDays(r.boughtWithinDays()));
    String category = r.category() == null || r.category().isBlank() ? null : r.category().trim();
    return customerRepository.audience(since, category);
  }

  private String promoText(String message) {
    String footer = props.promoFooter() == null ? "" : props.promoFooter().trim();
    return footer.isEmpty() ? message.trim() : message.trim() + " " + footer;
  }

  private static String describe(CampaignRequest r) {
    StringBuilder s = new StringBuilder("Customers who agreed to promotions");
    if (r.boughtWithinDays() != null) {
      s.append(", bought in the last ").append(r.boughtWithinDays()).append(" days");
    }
    if (r.category() != null && !r.category().isBlank()) {
      s.append(", bought ").append(r.category().trim());
    }
    return s.toString();
  }
}
