package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Sales. The list query returns summaries directly (one query, joined cashier name). */
public interface SaleRepository extends JpaRepository<Sale, Long> {

  Optional<Sale> findByClientRef(String clientRef);

  /** Receipt numbers come from a database sequence, so two tills never get the same one. */
  @Query(value = "select nextval('sale_receipt_seq')", nativeQuery = true)
  long nextReceiptNumber();

  @Query("select s from Sale s join fetch s.cashier where s.id = :id")
  Optional<Sale> findDetail(long id);

  @Query("select s from Sale s join fetch s.cashier where s.receiptNo = :receiptNo")
  Optional<Sale> findByReceiptNo(String receiptNo);

  /** cashierId null = all cashiers; receipt null = any; statuses: pass all values for any. */
  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos$SaleSummary(
              s.id, s.receiptNo, s.createdAt, c.fullName, cast(s.status as string), s.total,
              s.customerName)
          from Sale s join s.cashier c
          where s.createdAt >= :from and s.createdAt < :to
            and (:cashierId is null or c.id = :cashierId)
            and (:receipt is null or s.receiptNo = :receipt)
            and s.status in :statuses
          """,
      countQuery =
          """
          select count(s) from Sale s
          where s.createdAt >= :from and s.createdAt < :to
            and (:cashierId is null or s.cashier.id = :cashierId)
            and (:receipt is null or s.receiptNo = :receipt)
            and s.status in :statuses
          """)
  Page<SaleSummary> summaries(
      Instant from,
      Instant to,
      Long cashierId,
      String receipt,
      Collection<SaleStatus> statuses,
      Pageable pageable);

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos$SaleSummary(
              s.id, s.receiptNo, s.createdAt, c.fullName, cast(s.status as string), s.total,
              s.customerName)
          from Sale s join s.cashier c
          where s.customerId = :customerId
          """,
      countQuery = "select count(s) from Sale s where s.customerId = :customerId")
  Page<SaleSummary> forCustomer(long customerId, Pageable pageable);
}
