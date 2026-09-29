package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductService;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock counts. Starting a count notes what the system expects for every active product (or one
 * category). Staff type what they count; on finishing, the difference is applied to the current
 * stock, so sales made while counting are not lost.
 *
 * <p>IMEI/serial products are not changed by a count - which IMEIs are missing matters - so their
 * differences are listed to be fixed with Adjust stock, naming the IMEIs.
 */
@Service
public class StockCountService {

  private static final int MAX_PRODUCTS = 5000;

  public record CountLineView(
      long productId,
      String name,
      String category,
      String serialType,
      int expected,
      Integer counted,
      Integer difference) {}

  public record CountView(
      long id,
      String status,
      String category,
      Instant startedAt,
      Instant finishedAt,
      int products,
      int countedProducts,
      BigDecimal differenceValue,
      List<CountLineView> lines) {}

  public record CountEntry(long productId, Integer counted) {}

  private final StockCountRepository counts;
  private final ProductService products;
  private final StockService stock;
  private final Clock clock;

  public StockCountService(
      StockCountRepository counts, ProductService products, StockService stock, Clock clock) {
    this.counts = counts;
    this.products = products;
    this.stock = stock;
    this.clock = clock;
  }

  @Transactional
  public CountView start(String category, long userId) {
    if (counts.findFirstByStatus(StockCount.Status.OPEN).isPresent()) {
      throw ApiException.conflict("A stock count is already open. Finish or cancel it first.");
    }
    String cat = category == null || category.isBlank() ? null : category.trim();
    List<Product> list =
        products
            .load(null, cat, true, false, PageRequest.of(0, MAX_PRODUCTS, Sort.by("name")))
            .getContent();
    if (list.isEmpty()) {
      throw ApiException.badRequest("There are no active products to count.");
    }
    StockCount count = new StockCount(cat, userId, clock.instant());
    list.forEach(p -> count.getLines().add(new StockCountLine(count, p)));
    return view(counts.save(count));
  }

  @Transactional(readOnly = true)
  public CountView get(long id) {
    return view(find(id));
  }

  @Transactional(readOnly = true)
  public List<CountView> recent() {
    return counts.findTop10ByOrderByStartedAtDesc().stream().map(c -> summary(c)).toList();
  }

  /** Saves counted numbers as staff go (they can count over several sittings). */
  @Transactional
  public CountView record(long id, List<CountEntry> entries) {
    StockCount count = findOpen(id);
    Map<Long, StockCountLine> byProduct = new HashMap<>();
    count.getLines().forEach(l -> byProduct.put(l.getProduct().getId(), l));
    for (CountEntry e : entries) {
      StockCountLine line = byProduct.get(e.productId());
      if (line == null) {
        throw ApiException.badRequest("A product is not part of this count.");
      }
      if (e.counted() != null && e.counted() < 0) {
        throw ApiException.badRequest("Counts can't be negative.");
      }
      line.setCounted(e.counted());
    }
    return view(count);
  }

  @Transactional
  public CountView finish(long id, long userId) {
    StockCount count = findOpen(id);
    String reference = "COUNT-" + id;
    for (StockCountLine line : count.getLines()) {
      Integer diff = line.difference();
      if (diff != null && diff != 0 && line.getProduct().getSerialType() == SerialType.NONE) {
        stock.recordCount(line.getProduct().getId(), diff, reference, userId);
      }
    }
    count.close(StockCount.Status.FINISHED, userId, clock.instant());
    return view(count);
  }

  @Transactional
  public CountView cancel(long id, long userId) {
    StockCount count = findOpen(id);
    count.close(StockCount.Status.CANCELLED, userId, clock.instant());
    return summary(count);
  }

  private StockCount find(long id) {
    return counts.findDetail(id).orElseThrow(() -> ApiException.notFound("No such stock count."));
  }

  private StockCount findOpen(long id) {
    StockCount count = find(id);
    if (count.getStatus() != StockCount.Status.OPEN) {
      throw ApiException.conflict("This count is already closed.");
    }
    return count;
  }

  private CountView view(StockCount c) {
    List<CountLineView> lines = new ArrayList<>();
    for (StockCountLine l : c.getLines()) {
      Product p = l.getProduct();
      lines.add(
          new CountLineView(
              p.getId(),
              p.getName(),
              p.getCategory(),
              p.getSerialType().name(),
              l.getExpected(),
              l.getCounted(),
              l.difference()));
    }
    return build(c, lines);
  }

  private CountView summary(StockCount c) {
    return build(c, null);
  }

  private CountView build(StockCount c, List<CountLineView> lines) {
    int counted = 0;
    BigDecimal value = BigDecimal.ZERO.setScale(2);
    for (StockCountLine l : c.getLines()) {
      if (l.getCounted() != null) {
        counted++;
        value = value.add(l.getUnitCost().multiply(BigDecimal.valueOf(l.difference())));
      }
    }
    return new CountView(
        c.getId(),
        c.getStatus().name(),
        c.getCategory(),
        c.getStartedAt(),
        c.getFinishedAt(),
        c.getLines().size(),
        counted,
        value,
        lines);
  }
}
