package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.text.DecimalFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.Customer;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignPreview;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.MessageView;
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

  private final SmsMessageRepository messages;
  private final SmsCampaignRepository campaigns;
  private final CustomerRepository customers;
  private final SmsSender sender;
  private final SmsProperties props;
  private final ShopProperties shop;
  private final Clock clock;

  public MessageService(
      SmsMessageRepository messages,
      SmsCampaignRepository campaigns,
      CustomerRepository customers,
      SmsSender sender,
      SmsProperties props,
      ShopProperties shop,
      Clock clock) {
    this.messages = messages;
    this.campaigns = campaigns;
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

  /** Queues a promotion to every opted-in customer in the audience. */
  @Transactional
  public CampaignResult sendCampaign(CampaignRequest r, long userId) {
    List<Customer> audience = audience(r);
    if (audience.isEmpty()) {
      throw ApiException.badRequest("No customers who agreed to promotions match this audience.");
    }
    Instant now = clock.instant();
    String full = promoText(r.message());
    SmsCampaign campaign =
        campaigns.save(new SmsCampaign(full, describe(r), audience.size(), userId, now));
    for (Customer c : audience) {
      messages.save(
          new SmsMessage(
              c.getPhone(), full, SmsMessage.Kind.PROMO, c.getId(), null, campaign.getId(), now));
    }
    return new CampaignResult(campaign.getId(), audience.size(), SmsText.parts(full));
  }

  @Transactional(readOnly = true)
  public Page<MessageView> list(SmsMessage.Kind kind, SmsMessage.Status status, Pageable pageable) {
    return messages.search(kind, status, pageable);
  }

  /** Queued message ids, oldest first, for the dispatcher. */
  @Transactional(readOnly = true)
  public List<Long> queued() {
    return messages.queuedIds(PageRequest.of(0, BATCH));
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
    return customers.audience(since, category);
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
