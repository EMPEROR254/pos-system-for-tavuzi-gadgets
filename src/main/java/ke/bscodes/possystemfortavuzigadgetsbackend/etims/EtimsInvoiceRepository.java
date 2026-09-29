package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** The eTIMS outbox. */
public interface EtimsInvoiceRepository extends JpaRepository<EtimsInvoice, Long> {

  @Query(value = "select nextval('etims_invoice_seq')", nativeQuery = true)
  long nextInvoiceNo();

  @Query(
      "select i.id from EtimsInvoice i where i.status = 'PENDING' and i.nextAttemptAt <= :now"
          + " order by i.invoiceNo")
  List<Long> due(Instant now, Pageable pageable);

  Optional<EtimsInvoice> findFirstBySaleIdAndKind(long saleId, EtimsInvoice.Kind kind);

  List<EtimsInvoice> findBySaleIdOrderByInvoiceNo(long saleId);

  Optional<EtimsInvoice> findByReturnId(long returnId);

  long countByStatus(EtimsInvoice.Status status);

  /** The eTIMS list with each sale's receipt number, in one query. */
  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService$InvoiceView(
              i.id, i.invoiceNo, cast(i.kind as string), i.saleId, s.receiptNo,
              cast(i.status as string), i.attempts, i.lastError, i.createdAt, i.sentAt)
          from EtimsInvoice i, Sale s
          where s.id = i.saleId and (:status is null or i.status = :status)
          order by i.invoiceNo desc
          """,
      countQuery =
          "select count(i) from EtimsInvoice i where (:status is null or i.status = :status)")
  Page<EtimsService.InvoiceView> search(EtimsInvoice.Status status, Pageable pageable);
}
