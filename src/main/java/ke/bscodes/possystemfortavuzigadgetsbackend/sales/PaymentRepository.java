package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/** Payments; used to stop an M-Pesa code paying for two sales. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  boolean existsByMethodInAndReferenceIn(
      Collection<PaymentMethod> methods, Collection<String> references);
}
