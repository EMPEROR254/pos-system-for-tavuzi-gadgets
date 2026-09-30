package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos.PostSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Posts. The list is a projection, so the pictures are not loaded. */
public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.social.SocialDtos$PostSummary(
              p.id, p.title, p.description, p.caption, p.imageToken, u.fullName, p.createdAt)
          from SocialPost p, User u where u.id = p.createdBy
          order by p.createdAt desc, p.id desc
          """,
      countQuery = "select count(p) from SocialPost p")
  Page<PostSummary> recent(Pageable pageable);

  @Query("select p.image from SocialPost p where p.imageToken = :token")
  Optional<byte[]> imageByToken(String token);
}
