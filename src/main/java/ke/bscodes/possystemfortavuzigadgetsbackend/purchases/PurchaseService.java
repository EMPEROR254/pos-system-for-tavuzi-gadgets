package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductUnit;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PaymentRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseLineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseLineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseView;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.StatementLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.SupplierRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.SupplierView;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppliers, deliveries and what the shop owes. Receiving a delivery adds the stock (and its IMEIs)
 * and updates each product's average cost; anything not paid on delivery is owed to the supplier
 * until paid.
 */
@Service
public class PurchaseService {

  private final SupplierRepository suppliers;
  private final PurchaseRepository purchases;
  private final SupplierPaymentRepository payments;
  private final ProductRepository products;
  private final StockService stock;
  private final Clock clock;

  public PurchaseService(
      SupplierRepository suppliers,
      PurchaseRepository purchases,
      SupplierPaymentRepository payments,
      ProductRepository products,
      StockService stock,
      Clock clock) {
    this.suppliers = suppliers;
    this.purchases = purchases;
    this.payments = payments;
    this.products = products;
    this.stock = stock;
    this.clock = clock;
  }

  // ---- suppliers ----

  @Transactional(readOnly = true)
  public Page<SupplierView> searchSuppliers(String query, Boolean active, Pageable pageable) {
    String pattern =
        query == null || query.isBlank()
            ? null
            : "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "") + "%";
    return suppliers.search(pattern, active, pageable);
  }

  @Transactional
  public SupplierView createSupplier(SupplierRequest r) {
    if (suppliers.existsByNameIgnoreCase(r.name().trim())) {
      throw ApiException.conflict("A supplier with that name already exists.");
    }
    Supplier s = new Supplier(clock.instant());
    apply(s, r);
    return view(suppliers.save(s).getId());
  }

  @Transactional
  public SupplierView updateSupplier(long id, SupplierRequest r) {
    Supplier s = findSupplier(id);
    if (suppliers.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
      throw ApiException.conflict("A supplier with that name already exists.");
    }
    apply(s, r);
    suppliers.flush();
    return view(id);
  }

  @Transactional(readOnly = true)
  public SupplierView supplier(long id) {
    return view(id);
  }

  /** Deliveries (+) and payments (-) in date order, with the running balance owed. */
  @Transactional(readOnly = true)
  public List<StatementLine> statement(long supplierId) {
    findSupplier(supplierId);
    record Entry(Instant date, String description, BigDecimal amount) {}
    List<Entry> entries = new ArrayList<>();
    for (Purchase p : purchases.forStatement(supplierId)) {
      String invoice = p.getInvoiceNo() == null ? "" : " (invoice " + p.getInvoiceNo() + ")";
      entries.add(new Entry(p.getReceivedAt(), "Delivery #" + p.getId() + invoice, p.getTotal()));
    }
    for (SupplierPayment pay : payments.forStatement(supplierId)) {
      String ref = pay.getReference() == null ? "" : " " + pay.getReference();
      entries.add(
          new Entry(
              pay.getPaidAt(),
              "Payment by " + pay.getMethod().name().toLowerCase(Locale.ROOT) + ref,
              pay.getAmount().negate()));
    }
    entries.sort(Comparator.comparing(Entry::date));
    BigDecimal balance = BigDecimal.ZERO.setScale(2);
    List<StatementLine> lines = new ArrayList<>();
    for (Entry e : entries) {
      balance = balance.add(e.amount());
      lines.add(new StatementLine(e.date(), e.description(), e.amount(), balance));
    }
    return lines;
  }

  @Transactional
  public SupplierView pay(long supplierId, PaymentRequest r, long userId) {
    SupplierView current = view(supplierId);
    BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(current.balance()) > 0) {
      throw ApiException.badRequest(
          "That is more than you owe " + current.name() + " (KES " + current.balance() + ").");
    }
    payments.save(
        new SupplierPayment(
            supplierId,
            null,
            amount,
            r.method(),
            blankToNull(r.reference()),
            userId,
            clock.instant()));
    payments.flush();
    return view(supplierId);
  }

  // ---- deliveries ----

  @Transactional
  public PurchaseView receive(PurchaseRequest r, long userId) {
    Supplier supplier = findSupplier(r.supplierId());
    Map<Long, Product> catalogue =
        products
            .findWithUnits(
                r.lines().stream().map(PurchaseLineRequest::productId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    Purchase purchase =
        new Purchase(
            supplier, blankToNull(r.invoiceNo()), blankToNull(r.note()), userId, clock.instant());
    List<ProductUnit> units = new ArrayList<>();
    for (PurchaseLineRequest line : r.lines()) {
      Product product = catalogue.get(line.productId());
      if (product == null) {
        throw ApiException.badRequest("A product on this delivery doesn't exist.");
      }
      ProductUnit unit =
          product
              .unit(line.unitId())
              .orElseThrow(
                  () -> ApiException.badRequest(product.getName() + ": choose one of its units."));
      units.add(unit);
      purchase.addLine(
          new PurchaseLine(
              purchase,
              product,
              unit.getName(),
              unit.getFactor(),
              line.quantity(),
              line.unitCost().setScale(2, RoundingMode.HALF_UP)));
    }
    purchases.saveAndFlush(purchase);

    String reference = "DELIVERY-" + purchase.getId();
    for (int i = 0; i < r.lines().size(); i++) {
      PurchaseLineRequest line = r.lines().get(i);
      int factor = units.get(i).getFactor();
      BigDecimal costPerPiece =
          line.unitCost().divide(BigDecimal.valueOf(factor), 4, RoundingMode.HALF_UP);
      stock.recordPurchase(
          new StockService.Change(
              line.productId(),
              line.quantity() * factor,
              line.serials() == null ? List.of() : line.serials()),
          costPerPiece,
          reference,
          userId);
    }
    if (r.amountPaid() != null && r.amountPaid().signum() > 0) {
      if (r.paymentMethod() == null) {
        throw ApiException.badRequest("Choose how the supplier was paid.");
      }
      payments.save(
          new SupplierPayment(
              supplier.getId(),
              purchase.getId(),
              r.amountPaid().setScale(2, RoundingMode.HALF_UP),
              r.paymentMethod(),
              blankToNull(r.paymentReference()),
              userId,
              clock.instant()));
    }
    return view(purchase);
  }

  @Transactional(readOnly = true)
  public Page<PurchaseSummary> list(Long supplierId, Pageable pageable) {
    return purchases.summaries(supplierId, pageable);
  }

  @Transactional(readOnly = true)
  public PurchaseView get(long id) {
    return view(
        purchases.findDetail(id).orElseThrow(() -> ApiException.notFound("No such delivery.")));
  }

  private PurchaseView view(Purchase p) {
    return new PurchaseView(
        p.getId(),
        p.getSupplier().getId(),
        p.getSupplier().getName(),
        p.getInvoiceNo(),
        p.getNote(),
        p.getTotal(),
        p.getReceivedAt(),
        p.getLines().stream()
            .map(
                l ->
                    new PurchaseLineView(
                        l.getProduct().getId(),
                        l.getProduct().getName(),
                        l.getUnitName(),
                        l.getQuantity(),
                        l.getUnitCost(),
                        l.getLineTotal()))
            .toList());
  }

  private SupplierView view(long id) {
    return suppliers.findView(id).orElseThrow(() -> ApiException.notFound("No such supplier."));
  }

  private Supplier findSupplier(long id) {
    return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("No such supplier."));
  }

  private static void apply(Supplier s, SupplierRequest r) {
    s.update(
        r.name().trim(),
        blankToNull(r.phone()),
        blankToNull(r.email()),
        blankToNull(r.notes()),
        r.active());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
