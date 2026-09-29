package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Payments made to suppliers. */
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

  @Query("select p from SupplierPayment p where p.supplierId = :supplierId order by p.paidAt, p.id")
  List<SupplierPayment> forStatement(long supplierId);
}
