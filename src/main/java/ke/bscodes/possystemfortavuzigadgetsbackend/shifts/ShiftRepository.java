package ke.bscodes.possystemfortavuzigadgetsbackend.shifts;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Shifts. */
public interface ShiftRepository extends JpaRepository<Shift, Long> {

  Optional<Shift> findByCashierIdAndClosedAtIsNull(long cashierId);
}
