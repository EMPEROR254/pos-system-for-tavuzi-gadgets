package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.shifts.ShiftDtos.ShiftView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for shifts: the money taken in a shift, and the cash-up list for the owner. */
@Repository
public class ShiftQueries {

  private final JdbcClient jdbc;

  public ShiftQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The cashier's sales and cash refunds from opening until "until". Sales made offline count at
   * the time they were made, so they land in the shift they belong to.
   */
  public ShiftCash cash(long cashierId, Instant from, Instant until) {
    Timestamp start = Timestamp.from(from);
    Timestamp end = Timestamp.from(until);
    return jdbc.sql(
            """
            select
              (select count(*) from sales s
                 where s.cashier_id = :c and s.created_at >= :start and s.created_at <= :end) sales_count,
              (select coalesce(sum(p.amount) filter (where p.method = 'CASH'), 0) from payments p
                 join sales s on s.id = p.sale_id
                 where s.cashier_id = :c and s.created_at >= :start and s.created_at <= :end) cash_paid,
              (select coalesce(sum(s.change_due), 0) from sales s
                 where s.cashier_id = :c and s.created_at >= :start and s.created_at <= :end) change_given,
              (select coalesce(sum(r.refund_amount), 0) from sale_returns r
                 where r.processed_by = :c and r.refund_method = 'CASH'
                   and r.created_at >= :start and r.created_at <= :end) cash_refunds,
              (select coalesce(sum(p.amount) filter (where p.method in ('MPESA_TILL', 'MPESA_PAYBILL')), 0)
                 from payments p join sales s on s.id = p.sale_id
                 where s.cashier_id = :c and s.created_at >= :start and s.created_at <= :end) mpesa,
              (select coalesce(sum(p.amount) filter (where p.method = 'CARD'), 0) from payments p
                 join sales s on s.id = p.sale_id
                 where s.cashier_id = :c and s.created_at >= :start and s.created_at <= :end) card
            """)
        .param("c", cashierId)
        .param("start", start)
        .param("end", end)
        .query(
            (rs, n) ->
                new ShiftCash(
                    rs.getLong("sales_count"),
                    rs.getBigDecimal("cash_paid"),
                    rs.getBigDecimal("change_given"),
                    rs.getBigDecimal("cash_refunds"),
                    rs.getBigDecimal("mpesa"),
                    rs.getBigDecimal("card")))
        .single();
  }

  /** Shifts opened in the period, newest first, with the cashier's name. */
  public List<ShiftView> opened(Instant from, Instant to, Long cashierId) {
    return jdbc.sql(
            """
            select sh.*, u.full_name cashier_name, cb.full_name closed_by_name
            from shifts sh
            join users u on u.id = sh.cashier_id
            left join users cb on cb.id = sh.closed_by
            where sh.opened_at >= :start and sh.opened_at < :end
              and (cast(:cashier as bigint) is null or sh.cashier_id = :cashier)
            order by sh.opened_at desc, sh.id desc
            limit 500
            """)
        .param("start", Timestamp.from(from))
        .param("end", Timestamp.from(to))
        .param("cashier", cashierId)
        .query((rs, n) -> view(rs))
        .list();
  }

  public ShiftView one(long id) {
    return jdbc.sql(
            """
            select sh.*, u.full_name cashier_name, cb.full_name closed_by_name
            from shifts sh
            join users u on u.id = sh.cashier_id
            left join users cb on cb.id = sh.closed_by
            where sh.id = :id
            """)
        .param("id", id)
        .query((rs, n) -> view(rs))
        .single();
  }

  private static ShiftView view(ResultSet rs) throws SQLException {
    Timestamp closed = rs.getTimestamp("closed_at");
    return new ShiftView(
        rs.getLong("id"),
        rs.getLong("cashier_id"),
        rs.getString("cashier_name"),
        rs.getTimestamp("opened_at").toInstant(),
        closed == null ? null : closed.toInstant(),
        rs.getString("closed_by_name"),
        rs.getBigDecimal("opening_float"),
        rs.getBigDecimal("cash_sales"),
        rs.getBigDecimal("cash_refunds"),
        rs.getBigDecimal("expected_cash"),
        rs.getBigDecimal("counted_cash"),
        rs.getBigDecimal("difference"),
        rs.getString("note"));
  }
}
