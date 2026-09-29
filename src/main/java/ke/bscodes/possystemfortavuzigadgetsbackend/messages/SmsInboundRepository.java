package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.ReplyView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Replies from customers. */
public interface SmsInboundRepository extends JpaRepository<SmsInbound, Long> {

  boolean existsByProviderId(String providerId);

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos$ReplyView(
              i.id, i.phone, i.text, i.optedOut, i.receivedAt)
          from SmsInbound i order by i.receivedAt desc, i.id desc
          """,
      countQuery = "select count(i) from SmsInbound i")
  Page<ReplyView> recent(Pageable pageable);
}
