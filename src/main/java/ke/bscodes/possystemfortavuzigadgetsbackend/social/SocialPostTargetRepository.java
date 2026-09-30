package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Where each post goes, and what is due to be sent. */
public interface SocialPostTargetRepository extends JpaRepository<SocialPostTarget, Long> {

  @Query(
      "select t.id from SocialPostTarget t where t.status = 'PENDING' and t.nextAttemptAt <= :now"
          + " order by t.nextAttemptAt, t.id")
  List<Long> dueIds(Instant now, Pageable pageable);

  List<SocialPostTarget> findByPostIdInOrderByNetwork(Collection<Long> postIds);

  Optional<SocialPostTarget> findByPostIdAndNetwork(long postId, SocialNetwork network);
}
