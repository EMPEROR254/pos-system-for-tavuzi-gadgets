package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** IMEIs/serials already sold (and not returned). */
public interface SoldSerialRepository extends JpaRepository<SoldSerial, Long> {

  @Query(
      "select s.serial from SoldSerial s where upper(s.serial) in :serials and s.returnedAt is null")
  List<String> findActive(Collection<String> serials);
}
