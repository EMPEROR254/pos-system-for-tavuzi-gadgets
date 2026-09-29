package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Products. Lists load in two steps (a page of ids, then those products with their units in one
 * query) to avoid one query per product without paging in memory.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

  /** pattern: lower-case LIKE pattern or null; also matches SKU and unit barcodes. */
  @Query(
      value =
          """
          select p.id from Product p
          where (:pattern is null or lower(p.name) like :pattern or lower(p.sku) like :pattern
                 or exists (select 1 from ProductUnit u where u.product = p and u.barcode = :exact))
            and (:category is null or p.category = :category)
            and (:active is null or p.active = :active)
            and (:lowOnly = false or (p.reorderLevel > 0 and p.stockOnHand <= p.reorderLevel))
          """,
      countQuery =
          """
          select count(p) from Product p
          where (:pattern is null or lower(p.name) like :pattern or lower(p.sku) like :pattern
                 or exists (select 1 from ProductUnit u where u.product = p and u.barcode = :exact))
            and (:category is null or p.category = :category)
            and (:active is null or p.active = :active)
            and (:lowOnly = false or (p.reorderLevel > 0 and p.stockOnHand <= p.reorderLevel))
          """)
  Page<Long> searchIds(
      String pattern,
      String exact,
      String category,
      Boolean active,
      boolean lowOnly,
      Pageable pageable);

  @Query(
      "select count(p) from Product p"
          + " where p.active = true and p.reorderLevel > 0 and p.stockOnHand <= p.reorderLevel")
  long countLowStock();

  /** Value of what is on the shelves at cost (negative counts are ignored). */
  @Query(
      "select coalesce(sum(p.stockOnHand * p.costPrice), 0) from Product p where p.stockOnHand > 0")
  BigDecimal stockValue();

  @Query("select distinct p from Product p left join fetch p.units where p.id in :ids")
  List<Product> findWithUnits(Collection<Long> ids);

  /** A scanned barcode or typed SKU that matches exactly (the till adds it straight away). */
  @Query(
      """
      select distinct p from Product p left join fetch p.units
      where p.active = true
        and (lower(p.sku) = lower(:code)
             or exists (select 1 from ProductUnit u where u.product = p and u.barcode = :code))
      """)
  List<Product> findActiveByCode(String code);

  @Query(
      "select distinct p.category from Product p where p.category is not null order by p.category")
  List<String> categories();

  boolean existsBySkuIgnoreCase(String sku);

  boolean existsBySkuIgnoreCaseAndIdNot(String sku, long id);

  @Query(
      """
      select count(u) > 0 from ProductUnit u
      where u.barcode in :barcodes and (:productId is null or u.product.id <> :productId)
      """)
  boolean barcodeUsedElsewhere(Collection<String> barcodes, Long productId);
}
