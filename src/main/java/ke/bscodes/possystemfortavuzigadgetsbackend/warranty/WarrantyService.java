package ke.bscodes.possystemfortavuzigadgetsbackend.warranty;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warranty check: find what was sold by IMEI/serial number or receipt number and whether its
 * warranty is still running. Plain SQL across sales, lines and serials (one query).
 */
@Service
public class WarrantyService {

  public enum Status {
    ACTIVE,
    EXPIRED,
    NO_WARRANTY,
    RETURNED
  }

  public record WarrantyResult(
      String receiptNo,
      long saleId,
      Instant soldAt,
      String productName,
      String serial,
      Instant warrantyEndsAt,
      Status status,
      String customerName,
      String customerPhone) {}

  private static final String SQL =
      """
      select s.id as sale_id, s.receipt_no, s.created_at, s.customer_name, s.customer_phone,
             l.product_name, l.warranty_ends_at, l.quantity, l.returned_quantity,
             ss.serial, ss.returned_at
      from sale_lines l
      join sales s on s.id = l.sale_id
      left join sold_serials ss on ss.sale_line_id = l.id
      where upper(ss.serial) = :q or s.receipt_no = :q
      order by s.created_at desc, l.line_no
      limit 50
      """;

  private final JdbcClient jdbc;
  private final Clock clock;

  public WarrantyService(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<WarrantyResult> lookup(String query) {
    String q = Serials.normalise(query);
    if (q.length() < 3) {
      return List.of();
    }
    Instant now = clock.instant();
    return jdbc.sql(SQL)
        .param("q", q)
        .query(
            (rs, i) -> {
              Instant endsAt = instant(rs.getTimestamp("warranty_ends_at"));
              boolean returned =
                  rs.getString("serial") != null
                      ? rs.getTimestamp("returned_at") != null
                      : rs.getInt("returned_quantity") >= rs.getInt("quantity");
              Status status =
                  returned
                      ? Status.RETURNED
                      : endsAt == null
                          ? Status.NO_WARRANTY
                          : endsAt.isAfter(now) ? Status.ACTIVE : Status.EXPIRED;
              return new WarrantyResult(
                  rs.getString("receipt_no"),
                  rs.getLong("sale_id"),
                  instant(rs.getTimestamp("created_at")),
                  rs.getString("product_name"),
                  rs.getString("serial"),
                  endsAt,
                  status,
                  rs.getString("customer_name"),
                  rs.getString("customer_phone"));
            })
        .list();
  }

  private static Instant instant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
