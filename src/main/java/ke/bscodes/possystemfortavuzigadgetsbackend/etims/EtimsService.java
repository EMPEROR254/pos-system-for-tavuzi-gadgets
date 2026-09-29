package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsInvoice.Kind;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsInvoice.Status;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.Sale;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleReturn;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleReturnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * KRA eTIMS invoices. A sale or return only *queues* its invoice (in the same transaction, so
 * nothing is ever lost); EtimsDispatcher sends queued invoices in the background, retrying with
 * growing gaps while KRA or the internet is down. The till never waits for KRA.
 */
@Service
public class EtimsService {

  private static final Logger log = LoggerFactory.getLogger(EtimsService.class);
  private static final int BATCH = 20;

  /** What a receipt shows about its eTIMS invoice. */
  public record ReceiptInfo(
      String status,
      long invoiceNo,
      Long scuReceiptNo,
      Long totalReceiptNo,
      String internalData,
      String signature,
      String scuId,
      String mrcNo,
      String scuDate,
      String verifyUrl) {}

  public record InvoiceView(
      long id,
      long invoiceNo,
      String kind,
      long saleId,
      String receiptNo,
      String status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant sentAt) {}

  public record Summary(String mode, long pending, long sent, long rejected) {}

  private final EtimsInvoiceRepository invoices;
  private final SaleRepository sales;
  private final SaleReturnRepository returns;
  private final ProductRepository products;
  private final EtimsClient client;
  private final EtimsProperties props;
  private final Clock clock;

  public EtimsService(
      EtimsInvoiceRepository invoices,
      SaleRepository sales,
      SaleReturnRepository returns,
      ProductRepository products,
      EtimsClient client,
      EtimsProperties props,
      Clock clock) {
    this.invoices = invoices;
    this.sales = sales;
    this.returns = returns;
    this.products = products;
    this.client = client;
    this.props = props;
    this.clock = clock;
  }

  /** Called at checkout, inside the sale's transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void queueSale(long saleId) {
    invoices.save(
        new EtimsInvoice(invoices.nextInvoiceNo(), Kind.SALE, saleId, null, null, clock.instant()));
  }

  /**
   * Called when items are returned, inside the return's transaction: a credit note. A sale from
   * before eTIMS was switched on gets its own invoice first, as KRA needs the original.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void queueRefund(long saleId, long returnId) {
    Instant now = clock.instant();
    long original =
        invoices
            .findFirstBySaleIdAndKind(saleId, Kind.SALE)
            .orElseGet(
                () ->
                    invoices.save(
                        new EtimsInvoice(
                            invoices.nextInvoiceNo(), Kind.SALE, saleId, null, null, now)))
            .getInvoiceNo();
    invoices.save(
        new EtimsInvoice(invoices.nextInvoiceNo(), Kind.REFUND, saleId, returnId, original, now));
  }

  /** Invoices due to be sent now (none while eTIMS is switched off). */
  @Transactional(readOnly = true)
  public List<Long> due() {
    if (!props.enabled()) {
      return List.of();
    }
    return invoices.due(clock.instant(), PageRequest.of(0, BATCH));
  }

  /** Sends one invoice (its own transaction, so one failure doesn't block the rest). */
  @Transactional
  public void send(long invoiceId) {
    EtimsInvoice invoice = invoices.findById(invoiceId).orElse(null);
    if (invoice == null || invoice.getStatus() != Status.PENDING) {
      return;
    }
    Instant now = clock.instant();
    Sale sale = sales.findDetail(invoice.getSaleId()).orElseThrow();
    try {
      Map<String, Object> request;
      if (invoice.getKind() == Kind.SALE) {
        registerItems(sale.getLines());
        request = EtimsMapper.sale(sale, invoice.getInvoiceNo(), props.defaultItemClass());
      } else {
        // KRA must have the sale's invoice before its credit note.
        EtimsInvoice original =
            invoices.findFirstBySaleIdAndKind(sale.getId(), Kind.SALE).orElseThrow();
        if (original.getStatus() != Status.SENT) {
          invoice.postpone(
              "Waiting for the sale's invoice to reach KRA", now.plus(Duration.ofMinutes(1)));
          return;
        }
        SaleReturn ret = returns.findById(invoice.getReturnId()).orElseThrow();
        request =
            EtimsMapper.refund(
                sale,
                invoice.getInvoiceNo(),
                invoice.getOriginalInvoiceNo(),
                ret.getLines().stream()
                    .map(
                        l ->
                            new EtimsMapper.Item(
                                l.getSaleLine(), l.getQuantity(), l.getAmount(), l.getVatAmount()))
                    .toList(),
                ret.getCreatedAt(),
                props.defaultItemClass());
      }
      invoice.markSent(client.saveSale(request), now);
    } catch (EtimsClient.Unreachable e) {
      log.warn("eTIMS invoice {} not sent yet: {}", invoice.getInvoiceNo(), e.getMessage());
      invoice.retryLater(e.getMessage(), now);
    } catch (EtimsClient.Rejected e) {
      log.warn("eTIMS invoice {} rejected: {}", invoice.getInvoiceNo(), e.getMessage());
      invoice.reject(e.getMessage());
    }
  }

  /** KRA needs each product registered before it appears on an invoice (done once). */
  private void registerItems(List<SaleLine> lines) {
    List<Long> ids = lines.stream().map(SaleLine::getProductId).distinct().toList();
    for (Product p : products.findWithUnits(ids)) {
      if (p.getEtimsRegisteredAt() != null) {
        continue;
      }
      String itemClass =
          p.getEtimsItemClass() == null ? props.defaultItemClass() : p.getEtimsItemClass();
      client.saveItem(EtimsMapper.item(p, itemClass, p.getVatCategory().etimsCode()));
      p.markEtimsRegistered(clock.instant());
    }
  }

  /** A person fixed the reason KRA refused an invoice: send it again. */
  @Transactional
  public InvoiceView retry(long invoiceId) {
    EtimsInvoice invoice =
        invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("No such invoice."));
    if (invoice.getStatus() == Status.SENT) {
      throw ApiException.conflict("KRA already has this invoice.");
    }
    invoice.retryNow(clock.instant());
    return view(invoice);
  }

  @Transactional(readOnly = true)
  public ReceiptInfo forReceipt(long saleId) {
    return invoices
        .findFirstBySaleIdAndKind(saleId, Kind.SALE)
        .map(
            i ->
                new ReceiptInfo(
                    i.getStatus().name(),
                    i.getInvoiceNo(),
                    i.getScuReceiptNo(),
                    i.getTotalReceiptNo(),
                    i.getInternalData(),
                    i.getReceiptSignature(),
                    i.getScuId(),
                    i.getMrcNo(),
                    i.getScuDate(),
                    i.getReceiptSignature() == null
                        ? null
                        : props.verifyUrl(i.getReceiptSignature())))
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceView> list(Status status, Pageable pageable) {
    return invoices.search(status, pageable);
  }

  @Transactional(readOnly = true)
  public Summary summary() {
    return new Summary(
        props.mode().name(),
        invoices.countByStatus(Status.PENDING),
        invoices.countByStatus(Status.SENT),
        invoices.countByStatus(Status.REJECTED));
  }

  private InvoiceView view(EtimsInvoice i) {
    String receiptNo = sales.findById(i.getSaleId()).map(Sale::getReceiptNo).orElse(null);
    return new InvoiceView(
        i.getId(),
        i.getInvoiceNo(),
        i.getKind().name(),
        i.getSaleId(),
        receiptNo,
        i.getStatus().name(),
        i.getAttempts(),
        i.getLastError(),
        i.getCreatedAt(),
        i.getSentAt());
  }
}
