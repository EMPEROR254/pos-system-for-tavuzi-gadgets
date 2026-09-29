package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import org.springframework.data.jpa.repository.JpaRepository;

/** Phone numbers that opted out of promotions. */
public interface SmsOptOutRepository extends JpaRepository<SmsOptOut, String> {}
