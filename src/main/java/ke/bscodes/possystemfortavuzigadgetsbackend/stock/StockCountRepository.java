package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Stock counts. */
public interface StockCountRepository extends JpaRepository<StockCount, Long> {

  @Query(
      "select distinct c from StockCount c left join fetch c.lines l left join fetch l.product"
          + " where c.id = :id")
  Optional<StockCount> findDetail(long id);

  Optional<StockCount> findFirstByStatus(StockCount.Status status);

  List<StockCount> findTop10ByOrderByStartedAtDesc();

  @Query("select max(c.finishedAt) from StockCount c where c.status = 'FINISHED'")
  Instant lastFinishedAt();
}
