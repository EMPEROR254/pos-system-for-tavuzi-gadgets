package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.AdjustRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockSerial.Status;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.Serials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place stock changes. Every change locks the product rows, updates the count and writes a
 * movement (the stock history). IMEI/serial products also track each unit: the till can only sell
 * an IMEI that is on the shelf, so stock and IMEIs always agree.
 *
 * <p>Plain items may go below zero when the till sells what the system thinks is gone (the customer
 * is not turned away); the weekly count then corrects it.
 */
@Service
public class StockService {

  /** A change for one product: base units (+ in, - out) and the IMEIs/serials involved. */
  public record Change(long productId, int baseUnits, List<String> serials) {}

  private final EntityManager entityManager;
  private final StockMovementRepository movements;
  private final StockSerialRepository serials;
  private final Clock clock;

  public StockService(
      EntityManager entityManager,
      StockMovementRepository movements,
      StockSerialRepository serials,
      Clock clock) {
    this.entityManager = entityManager;
    this.movements = movements;
    this.serials = serials;
    this.clock = clock;
  }

  /** Stock out for a sale. IMEIs must be on the shelf and are marked sold. */
  @Transactional
  public void recordSale(List<Change> lines, String receiptNo, long userId) {
    Map<Long, Product> locked = lock(lines);
    Instant now = clock.instant();
    for (Change c : lines) {
      Product p = locked.get(c.productId());
      if (p.getSerialType() != SerialType.NONE) {
        takeFromShelf(p, c.serials(), Status.SOLD, now);
      }
      int balance = p.changeStock(-c.baseUnits());
      movements.save(
          new StockMovement(
              p.getId(),
              MovementType.SALE,
              -c.baseUnits(),
              balance,
              null,
              null,
              receiptNo,
              userId,
              now));
    }
  }

  /** Stock back in after a return; returned IMEIs go back on the shelf. */
  @Transactional
  public void recordReturn(List<Change> lines, String receiptNo, long userId) {
    Map<Long, Product> locked = lock(lines);
    Instant now = clock.instant();
    for (Change c : lines) {
      Product p = locked.get(c.productId());
      putOnShelf(p, c.serials(), true, now);
      int balance = p.changeStock(c.baseUnits());
      movements.save(
          new StockMovement(
              p.getId(),
              MovementType.RETURN,
              c.baseUnits(),
              balance,
              null,
              null,
              receiptNo,
              userId,
              now));
    }
  }

  /** Goods received from a supplier at a cost per base unit (updates the average cost). */
  @Transactional
  public void recordPurchase(Change c, BigDecimal costPerBaseUnit, String reference, long userId) {
    Product p = lock(List.of(c)).get(c.productId());
    Instant now = clock.instant();
    if (p.getSerialType() != SerialType.NONE) {
      requireSerialCount(p, c);
      putOnShelf(p, c.serials(), false, now);
    }
    p.receiveAtCost(c.baseUnits(), costPerBaseUnit);
    int balance = p.changeStock(c.baseUnits());
    movements.save(
        new StockMovement(
            p.getId(),
            MovementType.PURCHASE,
            c.baseUnits(),
            balance,
            null,
            null,
            reference,
            userId,
            now));
  }

  /** A change by hand (opening stock, damaged, lost, parts used for warranty repairs...). */
  @Transactional
  public int adjust(AdjustRequest r, long userId) {
    if (r.quantity() == 0) {
      throw ApiException.badRequest("Enter how many to add or remove.");
    }
    Change c =
        new Change(r.productId(), r.quantity(), r.serials() == null ? List.of() : r.serials());
    Product p = lock(List.of(c)).get(c.productId());
    Instant now = clock.instant();
    if (p.getSerialType() != SerialType.NONE) {
      requireSerialCount(p, new Change(c.productId(), Math.abs(r.quantity()), c.serials()));
      if (r.quantity() > 0) {
        putOnShelf(p, c.serials(), false, now);
      } else {
        takeFromShelf(p, c.serials(), Status.REMOVED, now);
      }
    }
    int balance = p.changeStock(r.quantity());
    movements.save(
        new StockMovement(
            p.getId(),
            MovementType.ADJUSTMENT,
            r.quantity(),
            balance,
            r.reason().name(),
            r.note() == null || r.note().isBlank() ? null : r.note().trim(),
            null,
            userId,
            now));
    return balance;
  }

  /** Stock count result: sets the difference found by counting. */
  @Transactional
  public void recordCount(long productId, int difference, String reference, long userId) {
    Product p = lock(List.of(new Change(productId, difference, List.of()))).get(productId);
    int balance = p.changeStock(difference);
    movements.save(
        new StockMovement(
            productId,
            MovementType.COUNT,
            difference,
            balance,
            null,
            null,
            reference,
            userId,
            clock.instant()));
  }

  /**
   * Locks the product rows (SELECT ... FOR UPDATE) and re-reads them, so two tills selling the same
   * item at the same moment can't overwrite each other's count. refresh() matters: the product may
   * already be loaded (e.g. to price the sale) with a count that is now out of date. Ids are sorted
   * so locks are always taken in the same order (no deadlocks).
   */
  private Map<Long, Product> lock(List<Change> changes) {
    Map<Long, Product> locked = new HashMap<>();
    changes.stream()
        .map(Change::productId)
        .distinct()
        .sorted()
        .forEach(
            id -> {
              Product p = entityManager.find(Product.class, id);
              if (p == null) {
                throw ApiException.notFound("No such product.");
              }
              entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE);
              locked.put(id, p);
            });
    return locked;
  }

  private static void requireSerialCount(Product p, Change c) {
    long given = c.serials().stream().map(Serials::normalise).filter(s -> !s.isEmpty()).count();
    if (given != c.baseUnits()) {
      String kind = p.getSerialType() == SerialType.IMEI ? "IMEI" : "serial number";
      throw ApiException.badRequest(
          p.getName()
              + ": list "
              + c.baseUnits()
              + " "
              + kind
              + (c.baseUnits() == 1 ? "" : "s")
              + " (one per item).");
    }
  }

  /** IMEIs coming in. They must not already be on a shelf. */
  private void putOnShelf(Product p, List<String> raw, boolean isReturn, Instant now) {
    List<String> wanted = normalise(p, raw);
    if (wanted.isEmpty()) {
      return;
    }
    Map<String, StockSerial> known = byUpperSerial(wanted);
    for (String serial : wanted) {
      StockSerial existing = known.get(serial);
      if (existing == null) {
        serials.save(new StockSerial(p.getId(), serial, now));
      } else if (existing.getStatus() == Status.IN_STOCK && !isReturn) {
        throw ApiException.conflict(serial + " is already in stock.");
      } else {
        existing.moveTo(Status.IN_STOCK, p.getId(), now);
      }
    }
  }

  /** IMEIs going out. Each must be on this product's shelf. */
  private void takeFromShelf(Product p, List<String> raw, Status newStatus, Instant now) {
    List<String> wanted = normalise(p, raw);
    Map<String, StockSerial> known = byUpperSerial(wanted);
    for (String serial : wanted) {
      StockSerial s = known.get(serial);
      if (s == null || s.getStatus() != Status.IN_STOCK || s.getProductId() != p.getId()) {
        throw ApiException.conflict(
            serial
                + " is not in stock for "
                + p.getName()
                + ". Receive it first (Stock → Purchases, or Adjust stock).");
      }
      s.moveTo(newStatus, p.getId(), now);
    }
  }

  private static List<String> normalise(Product p, List<String> raw) {
    List<String> result =
        raw == null
            ? List.of()
            : raw.stream().map(Serials::normalise).filter(s -> !s.isEmpty()).toList();
    Set<String> seen = new HashSet<>();
    for (String s : result) {
      String problem = Serials.problem(p.getSerialType(), s);
      if (problem != null) {
        throw ApiException.badRequest(p.getName() + ": " + problem);
      }
      if (!seen.add(s)) {
        throw ApiException.badRequest(s + " is listed twice.");
      }
    }
    return result;
  }

  private Map<String, StockSerial> byUpperSerial(List<String> wanted) {
    Map<String, StockSerial> known = new HashMap<>();
    if (!wanted.isEmpty()) {
      serials.findBySerials(wanted).forEach(s -> known.put(s.getSerial().toUpperCase(), s));
    }
    return known;
  }
}
