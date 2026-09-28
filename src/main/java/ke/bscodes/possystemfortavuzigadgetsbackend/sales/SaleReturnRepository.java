package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import org.springframework.data.jpa.repository.JpaRepository;

/** Returns (owner-approved). */
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {}
