package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.ApprovalService;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.mpesa.Phones;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.PaymentRules.CheckedPayment;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.PaymentRules.PaymentPlan;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleCalculator.PricedCart;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleCalculator.PricedLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.CheckoutRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Permission;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.RolePermissions;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.Vat;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout: prices the cart from the catalogue, checks discounts (supervisor approval for
 * cashiers), payments (split, M-Pesa codes used once), IMEIs (not sold twice), then saves the sale
 * with a new receipt number.
 */
@Service
public class SaleService {

  private static final List<PaymentMethod> MPESA =
      List.of(PaymentMethod.MPESA_TILL, PaymentMethod.MPESA_PAYBILL);

  private final SaleRepository sales;
  private final PaymentRepository payments;
  private final SoldSerialRepository soldSerials;
  private final ProductRepository products;
  private final UserRepository users;
  private final ApprovalService approvals;
  private final Vat vat;
  private final Clock clock;

  public SaleService(
      SaleRepository sales,
      PaymentRepository payments,
      SoldSerialRepository soldSerials,
      ProductRepository products,
      UserRepository users,
      ApprovalService approvals,
      Vat vat,
      Clock clock) {
    this.sales = sales;
    this.payments = payments;
    this.soldSerials = soldSerials;
    this.products = products;
    this.users = users;
    this.approvals = approvals;
    this.vat = vat;
    this.clock = clock;
  }

  @Transactional
  public SaleView checkout(AuthUser actor, CheckoutRequest request) {
    Optional<Sale> already = sales.findByClientRef(request.clientRef());
    if (already.isPresent()) {
      // The same sale sent again (double tap or retry): give back the first one.
      return SaleViews.of(visibleTo(actor, already.get()));
    }
    Instant now = clock.instant();
    List<Long> ids = request.lines().stream().map(LineRequest::productId).distinct().toList();
    Map<Long, Product> catalogue =
        products.findWithUnits(ids).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    PricedCart cart =
        SaleCalculator.price(request.lines(), catalogue, request.priceTier(), vat, now);

    Long discountApprovedBy =
        cart.hasDiscount()
            ? approvals.authorise(actor, Permission.GIVE_DISCOUNT, request.approval(), "A discount")
            : null;
    PaymentPlan plan = PaymentRules.check(request.payments(), cart.totals().total());
    checkMpesaCodesUnused(plan);
    checkSerialsNotSold(cart);

    String receiptNo = "TG%06d".formatted(sales.nextReceiptNumber());
    Sale sale =
        new Sale(
            receiptNo,
            request.clientRef(),
            users.getReferenceById(actor.id()),
            request.priceTier(),
            blankToNull(request.customerName()),
            phoneOrNull(request.customerPhone()),
            now);
    int lineNo = 1;
    for (PricedLine priced : cart.lines()) {
      SaleLine line = new SaleLine(sale, lineNo++, priced);
      priced.serials().forEach(line::addSerial);
      sale.addLine(line);
    }
    for (CheckedPayment p : plan.payments()) {
      sale.addPayment(new Payment(sale, p.method(), p.amount(), p.reference(), now));
    }
    var t = cart.totals();
    sale.setTotals(
        t.subtotal(),
        t.discountTotal(),
        t.vatTotal(),
        t.total(),
        plan.paid(),
        plan.change(),
        discountApprovedBy);
    return SaleViews.of(sales.saveAndFlush(sale));
  }

  @Transactional(readOnly = true)
  public SaleView get(AuthUser actor, long id) {
    return SaleViews.of(loadVisible(actor, id));
  }

  /** Cashiers see only their own sales; owners and managers (VIEW_REPORTS) see all. */
  @Transactional(readOnly = true)
  public Page<SaleSummary> list(
      AuthUser actor,
      Instant from,
      Instant to,
      Long cashierId,
      String receipt,
      SaleStatus status,
      Pageable pageable) {
    Long cashier = canSeeAll(actor) ? cashierId : Long.valueOf(actor.id());
    String receiptNo = receipt == null || receipt.isBlank() ? null : receipt.trim().toUpperCase();
    List<SaleStatus> statuses =
        status == null ? Arrays.asList(SaleStatus.values()) : List.of(status);
    return sales.summaries(from, to, cashier, receiptNo, statuses, pageable);
  }

  /** The sale if this person may see it (their own, or any with VIEW_REPORTS); else 404. */
  @Transactional(readOnly = true)
  public Sale loadVisible(AuthUser actor, long id) {
    Sale sale = sales.findDetail(id).orElseThrow(() -> ApiException.notFound("No such sale."));
    return visibleTo(actor, sale);
  }

  private Sale visibleTo(AuthUser actor, Sale sale) {
    if (!canSeeAll(actor) && sale.getCashier().getId() != actor.id()) {
      throw ApiException.notFound("No such sale.");
    }
    return sale;
  }

  private static boolean canSeeAll(AuthUser actor) {
    return RolePermissions.can(actor.role(), Permission.VIEW_REPORTS);
  }

  private void checkMpesaCodesUnused(PaymentPlan plan) {
    List<String> codes =
        plan.payments().stream()
            .filter(p -> p.method().isMpesa())
            .map(CheckedPayment::reference)
            .toList();
    if (!codes.isEmpty() && payments.existsByMethodInAndReferenceIn(MPESA, codes)) {
      throw ApiException.conflict(
          "That M-Pesa code was already used for another sale. Check the customer's SMS.");
    }
  }

  private void checkSerialsNotSold(PricedCart cart) {
    List<String> serials = cart.lines().stream().flatMap(l -> l.serials().stream()).toList();
    if (serials.isEmpty()) {
      return;
    }
    List<String> sold = soldSerials.findActive(serials);
    if (!sold.isEmpty()) {
      throw ApiException.conflict(sold.getFirst() + " was already sold. Check the number.");
    }
  }

  private static String phoneOrNull(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    try {
      return Phones.normalize(phone);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(
          "The customer's phone number doesn't look right (07XX XXX XXX).");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
