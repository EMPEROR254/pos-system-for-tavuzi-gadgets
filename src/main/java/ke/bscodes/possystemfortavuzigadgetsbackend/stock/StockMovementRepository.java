package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.MovementView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Stock history. The list joins the user's name in the same query. */
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos$MovementView(
              m.id, cast(m.type as string), m.quantity, m.balanceAfter, m.reason, m.note,
              m.reference, u.fullName, m.createdAt)
          from StockMovement m, User u
          where u.id = m.userId and m.productId = :productId
          order by m.createdAt desc, m.id desc
          """,
      countQuery = "select count(m) from StockMovement m where m.productId = :productId")
  Page<MovementView> history(long productId, Pageable pageable);
}
