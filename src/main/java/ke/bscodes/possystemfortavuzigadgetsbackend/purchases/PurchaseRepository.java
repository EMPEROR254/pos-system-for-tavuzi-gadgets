package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import java.util.List;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Deliveries from suppliers. */
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos$PurchaseSummary(
              p.id, s.name, p.invoiceNo, p.total, p.receivedAt)
          from Purchase p join p.supplier s
          where (:supplierId is null or s.id = :supplierId)
          order by p.receivedAt desc, p.id desc
          """,
      countQuery =
          "select count(p) from Purchase p where (:supplierId is null or p.supplier.id = :supplierId)")
  Page<PurchaseSummary> summaries(Long supplierId, Pageable pageable);

  @Query(
      "select distinct p from Purchase p join fetch p.supplier left join fetch p.lines l"
          + " left join fetch l.product where p.id = :id")
  Optional<Purchase> findDetail(long id);

  @Query("select p from Purchase p where p.supplier.id = :supplierId order by p.receivedAt, p.id")
  List<Purchase> forStatement(long supplierId);
}
