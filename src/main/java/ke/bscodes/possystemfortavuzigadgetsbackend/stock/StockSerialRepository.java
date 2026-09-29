package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** IMEIs/serials known to the shop. Serials are stored upper-case. */
public interface StockSerialRepository extends JpaRepository<StockSerial, Long> {

  @Query("select s from StockSerial s where upper(s.serial) in :serials")
  List<StockSerial> findBySerials(Collection<String> serials);

  @Query(
      "select s.serial from StockSerial s where s.productId = :productId"
          + " and s.status = 'IN_STOCK' order by s.serial")
  List<String> inStock(long productId);
}
