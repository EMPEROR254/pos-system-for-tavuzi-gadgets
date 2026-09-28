package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Staff accounts. Usernames are stored in lower case. */
public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  long countByRoleAndActiveTrue(Role role);

  /** pattern is a lower-case LIKE pattern ("%jane%") or null for no search. */
  @Query(
      """
      select u from User u
      where (:pattern is null or u.username like :pattern or lower(u.fullName) like :pattern)
        and (:role is null or u.role = :role)
        and (:active is null or u.active = :active)
      """)
  Page<User> search(String pattern, Role role, Boolean active, Pageable pageable);
}
