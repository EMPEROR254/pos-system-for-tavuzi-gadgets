package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.SupplierView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Suppliers, listed with their balances worked out by the database (one query). */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

  String VIEW =
      """
      select new ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos$SupplierView(
          s.id, s.name, s.phone, s.email, s.notes, s.active,
          coalesce((select sum(p.total) from Purchase p where p.supplier = s), 0),
          coalesce((select sum(sp.amount) from SupplierPayment sp where sp.supplierId = s.id), 0))
      from Supplier s
      """;

  @Query(
      value =
          VIEW
              + " where (:pattern is null or lower(s.name) like :pattern)"
              + " and (:active is null or s.active = :active)",
      countQuery =
          "select count(s) from Supplier s where (:pattern is null or lower(s.name) like :pattern)"
              + " and (:active is null or s.active = :active)")
  Page<SupplierView> search(String pattern, Boolean active, Pageable pageable);

  @Query(VIEW + " where s.id = :id")
  Optional<SupplierView> findView(long id);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
