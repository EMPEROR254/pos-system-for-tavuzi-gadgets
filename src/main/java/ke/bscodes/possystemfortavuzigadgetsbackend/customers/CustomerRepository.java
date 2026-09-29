package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Customers. Totals (visits, spend, last purchase) come from the sales table in one query. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

  String VIEW =
      """
      select new ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos$CustomerView(
          c.id, c.name, c.phone, c.email, c.notes, c.marketingOptIn,
          (select count(s) from Sale s where s.customerId = c.id),
          coalesce((select sum(s.total) from Sale s where s.customerId = c.id), 0),
          (select max(s.createdAt) from Sale s where s.customerId = c.id))
      from Customer c
      """;

  Optional<Customer> findByPhone(String phone);

  boolean existsByPhoneAndIdNot(String phone, long id);

  /** pattern: lower-case name pattern; phoneDigits: digits typed (matches anywhere in phone). */
  @Query(
      value =
          VIEW
              + " where (:pattern is null or lower(c.name) like :pattern or c.phone like :phone)"
              + " and (:optedIn is null or c.marketingOptIn = :optedIn)",
      countQuery =
          "select count(c) from Customer c"
              + " where (:pattern is null or lower(c.name) like :pattern or c.phone like :phone)"
              + " and (:optedIn is null or c.marketingOptIn = :optedIn)")
  Page<CustomerView> search(String pattern, String phone, Boolean optedIn, Pageable pageable);

  @Query(VIEW + " where c.id = :id")
  Optional<CustomerView> findView(long id);

  @Query(
      "select c from Customer c where lower(c.name) like :pattern or c.phone like :phone"
          + " order by c.name")
  List<Customer> quickSearch(String pattern, String phone, Pageable pageable);

  /**
   * Opted-in customers for a promotion. since: only those who bought on/after it (null = all);
   * category: only those who bought a product in it (null = any).
   */
  @Query(
      """
      select c from Customer c
      where c.marketingOptIn = true
        and (cast(:since as timestamp) is null or exists (
              select 1 from Sale s where s.customerId = c.id and s.createdAt >= :since))
        and (:category is null or exists (
              select 1 from Sale s join s.lines l, Product p
              where s.customerId = c.id and p.id = l.productId and p.category = :category))
      order by c.id
      """)
  List<Customer> audience(Instant since, String category);
}
