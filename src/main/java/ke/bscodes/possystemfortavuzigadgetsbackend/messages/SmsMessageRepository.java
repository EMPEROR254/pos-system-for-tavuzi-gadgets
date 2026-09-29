package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.MessageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** The SMS outbox. */
public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

  @Query("select m.id from SmsMessage m where m.status = 'QUEUED' order by m.id")
  List<Long> queuedIds(Pageable pageable);

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos$MessageView(
              m.id, m.phone, m.body, cast(m.kind as string), cast(m.status as string), m.attempts,
              m.error, m.createdAt, m.sentAt)
          from SmsMessage m
          where (:kind is null or m.kind = :kind) and (:status is null or m.status = :status)
          """,
      countQuery =
          "select count(m) from SmsMessage m"
              + " where (:kind is null or m.kind = :kind) and (:status is null or m.status = :status)")
  Page<MessageView> search(SmsMessage.Kind kind, SmsMessage.Status status, Pageable pageable);
}
