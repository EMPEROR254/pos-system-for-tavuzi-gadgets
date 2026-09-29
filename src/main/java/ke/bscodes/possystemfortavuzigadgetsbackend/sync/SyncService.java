package ke.bscodes.possystemfortavuzigadgetsbackend.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.SellableProduct;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offline support. Tills download the catalogue now and then so they can keep selling when they
 * can't reach the server; sales made then are kept at the till and sent when it can (their
 * clientRef stops a sale being saved twice). The shop sells 100-1000 products, so the whole
 * catalogue is small enough to send in one go.
 */
@Service
public class SyncService {

  private static final int MAX_PRODUCTS = 5000;

  public record Catalogue(
      Instant generatedAt,
      List<SellableProduct> products,
      Map<Long, List<String>> serialsInStock) {}

  public record Waiting(String etimsMode, long etimsPending, long etimsRejected, long smsQueued) {}

  private final ProductService products;
  private final EtimsService etims;
  private final JdbcClient jdbc;
  private final Clock clock;

  public SyncService(ProductService products, EtimsService etims, JdbcClient jdbc, Clock clock) {
    this.products = products;
    this.etims = etims;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Catalogue catalogue() {
    List<SellableProduct> all =
        products.sellable(PageRequest.of(0, MAX_PRODUCTS, Sort.by("name").and(Sort.by("id"))));
    Map<Long, List<String>> serials = new HashMap<>();
    jdbc.sql(
            "select product_id, serial from stock_serials where status = 'IN_STOCK' order by serial")
        .query(
            rs -> {
              serials.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
            });
    return new Catalogue(clock.instant(), all, serials);
  }

  @Transactional(readOnly = true)
  public Waiting waiting() {
    EtimsService.Summary s = etims.summary();
    long sms =
        jdbc.sql("select count(*) from sms_messages where status = 'QUEUED'")
            .query(Long.class)
            .single();
    return new Waiting(s.mode(), s.pending(), s.rejected(), sms);
  }
}
